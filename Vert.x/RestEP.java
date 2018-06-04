package DAD;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;

import io.vertx.mqtt.messages.MqttPublishMessage;

import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class RestEP extends AbstractVerticle {

	private HttpServer server;

	private SQLClient databaseClient;

	private MqttServer mqttServer;

	private MqttClient mqttCliente;
	
	private static Multimap<String, MqttEndpoint> clientTopics;


	@Override
	public void start() throws Exception {
		super.start();

		Router router = Router.router(vertx);
		
		clientTopics = HashMultimap.create();

		
		JsonObject mySQLClientConfig = new JsonObject().put("host", "127.0.0.1").put("port", 3306)
				.put("database", "invernadero").put("username", "root").put("password", "root");

		databaseClient = MySQLClient.createShared(vertx, mySQLClientConfig);

		vertx.createHttpServer().requestHandler(router::accept) // requestHandler filtra peticiones
				.listen(8081, res -> {
					if (res.succeeded())
						System.out.println("Servidor REST desplegado");
					else
						System.out.println("Error: " + res.cause());
				}

		);
		 Set<String> allowedHeaders = new HashSet<>();
		 allowedHeaders.add("Access-Control-Allow-Origin");
		
		router.route("/database/*").handler(BodyHandler.create()); // <-- "Directorio raiz" de tu servidor
		 router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders));
		router.get("/database/entera/:tabla/").handler(this::getTablaEntera);
		router.get("/database/medicionf/:tabla/:fecha1/:fecha2/").handler(routingContext -> {
			try {
				getMedicionEntreFechas(routingContext);
			} catch (ParseException e) {

				e.printStackTrace();
			}
		});
		router.get("/database/mediatotal/:tabla/").handler(this::getMediaTotal);
		router.get("/database/ultima/:tabla/").handler(this::getUltimaMedicion);
		router.put("/database/insercion/:tabla/").handler(this::putMed);
		router.post("/database/comando/").handler(this::postComando);
		
		
		mqttServer = MqttServer.create(vertx);
		init(mqttServer);
		
		mqttCliente = MqttClient.create(vertx, new MqttClientOptions().setAutoKeepAlive(true));

		mqttCliente.connect(8123, "localhost", handler -> {});
		
	/*	mqttCliente.subscribe("node", MqttQoS.AT_LEAST_ONCE.value(), msg -> {

			System.out.println("Mensaje recibido: " + msg.toString());

		});
*/

	}

	/**
	 * Método encargado de inicializar el servidor y ajustar todos los manejadores
	 * @param mqttServer
	 */
	private static void init(MqttServer mqttServer) {
		mqttServer.endpointHandler(endpoint -> {
			// Si se ejecuta este código es que un cliente se ha suscrito al servidor MQTT para 
			// algún topic.
			System.out.println("Nuevo cliente MQTT [" + endpoint.clientIdentifier()
					+ "] solicitando suscribirse [Id de sesión: " + endpoint.isCleanSession() + "]");
			// Indicamos al cliente que se ha contectado al servidor MQTT y que no tenía
			// sesión previamente creada (parámetro false)
			endpoint.accept(false);

			// Handler para gestionar las suscripciones a un determinado topic. Aquí registraremos
			// el cliente para poder reenviar todos los mensajes que se publicen en el topic al que
			// se ha suscrito.
			handleSubscription(endpoint);

			// Handler para gestionar las desuscripciones de un determinado topic. Haremos lo contrario
			// que el punto anterior para eliminar al cliente de la lista de clientes registrados en el 
			// topic. De este modo, no seguirá recibiendo mensajes en este topic.
			handleUnsubscription(endpoint);

			// Este handler será llamado cuando se publique un mensaje por parte del cliente en algún
			// topic creado en el servidor MQTT. En esta función obtendremos todos los clientes
			// suscritos a este topic y reenviaremos el mensaje a cada uno de ellos. Esta es la tarea
			// principal del broken MQTT. En este caso hemos implementado un broker muy muy sencillo. 
			// Para gestionar QoS, asegurar la entregar, guardar los mensajes en una BBDD para después
			// entregarlos, guardar los clientes en caso de caída del servidor, etc. debemos recurrir
			// a un código más elaborado o usar una solución existente como por ejemplo Mosquitto.
			publishHandler(endpoint);

			// Handler encargado de gestionar las desconexiones de los clientes al servidor. En este caso
			// eliminaremos al cliente de todos los topics a los que estuviera suscrito.
			handleClientDisconnect(endpoint);
		}).listen(8123,ar -> {
			if (ar.succeeded()) {
				System.out.println("MQTT server está a la escucha por el puerto " + ar.result().actualPort());
			} else {
				System.out.println("Error desplegando el MQTT server");
				ar.cause().printStackTrace();
			}
		});
	}

	/**
	 * Método encargado de gestionar las suscripciones de los clientes a los diferentes topics.
	 * En este método se registrará el cliente asociado al topic al que se suscribe
	 * @param endpoint
	 */
	private static void handleSubscription(MqttEndpoint endpoint) {
		endpoint.subscribeHandler(subscribe -> {
			// Los niveles de QoS permiten saber el tipo de entrega que se realizará:
			// - AT_LEAST_ONCE: Se asegura que los mensajes llegan a los clientes, pero no
			// que se haga una única vez (pueden llegar duplicados)
			// - EXACTLY_ONCE: Se asegura que los mensajes llegan a los clientes un única
			// vez (mecanismo más costoso)
			// - AT_MOST_ONCE: No se asegura que el mensaje llegue al cliente, por lo que no
			// es necesario ACK por parte de éste
			List<MqttQoS> grantedQosLevels = new ArrayList<>();
			for (MqttTopicSubscription s : subscribe.topicSubscriptions()) {
				System.out.println("Suscripción al topic " + s.topicName() + " con QoS " + s.qualityOfService());
				grantedQosLevels.add(s.qualityOfService());
				
				// Añadimos al cliente en la lista de clientes suscritos al topic
				clientTopics.put(s.topicName(), endpoint);
			}
		
			// Enviamos el ACK al cliente de que se ha suscrito al topic con los niveles de
			// QoS indicados
			endpoint.subscribeAcknowledge(subscribe.messageId(), grantedQosLevels);
		});
	}

	/**
	 * Método encargado de eliminar la suscripción de un cliente a un topic.
	 * En este método se eliminará al cliente de la lista de clientes suscritos a ese topic.
	 * @param endpoint
	 */
	private static void handleUnsubscription(MqttEndpoint endpoint) {
		endpoint.unsubscribeHandler(unsubscribe -> {
			for (String t : unsubscribe.topics()) {
				// Eliminos al cliente de la lista de clientes suscritos al topic
				clientTopics.remove(t, endpoint);
				System.out.println("Eliminada la suscripción del topic " + t);
			}
			// Informamos al cliente que la desuscripción se ha realizado
			endpoint.unsubscribeAcknowledge(unsubscribe.messageId());
		});
	}

	/**
	 * Manejador encargado de interceptar los envíos de mensajes de los diferentes clientes.
	 * Este método deberá procesar el mensaje, identificar los clientes suscritos al topic donde
	 * se publica dicho mensaje y enviar el mensaje a cada uno de esos clientes.
	 * @param endpoint
	 */
	private static void publishHandler(MqttEndpoint endpoint) {
		endpoint.publishHandler(message -> {
			// Suscribimos un handler cuando se solicite una publicación de un mensaje en un
			// topic
			handleMessage(message, endpoint);
		}).publishReleaseHandler(messageId -> {
			// Suscribimos un handler cuando haya finalizado la publicación del mensaje en
			// el topic
			endpoint.publishComplete(messageId);
		});
	}

	/**
	 * Método de utilidad para la gestión de los mensajes salientes.
	 * @param message
	 * @param endpoint
	 */
	private static void handleMessage(MqttPublishMessage message, MqttEndpoint endpoint) {
		System.out.println("Mensaje publicado por el cliente " + endpoint.clientIdentifier() + " en el topic "
				+ message.topicName());
		System.out.println("    Contenido del mensaje: " + message.payload().toString());
		
		// Obtenemos todos los clientes suscritos a ese topic (exceptuando el cliente que envía el 
		// mensaje) para así poder reenviar el mensaje a cada uno de ellos. Es aquí donde nuestro
		// código realiza las funciones de un broken MQTT
		System.out.println("Origen: " + endpoint.clientIdentifier());
		for (MqttEndpoint client: clientTopics.get(message.topicName())) {
			System.out.println("Destino: " + client.clientIdentifier());
			if (!client.clientIdentifier().equals(endpoint.clientIdentifier()))
				client.publish(message.topicName(), message.payload(), message.qosLevel(), message.isDup(), message.isRetain());
		}
		
		if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
			String topicName = message.topicName();
			switch (topicName) {
			// Se podría hacer algo con el mensaje como, por ejemplo, almacenar un registro
			// en la base de datos
			}
			// Envía el ACK al cliente de que el mensaje ha sido publicado
			endpoint.publishAcknowledge(message.messageId());
		} else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
			// Envía el ACK al cliente de que el mensaje ha sido publicado y cierra el canal
			// para este mensaje. Así se evita que los mensajes se publiquen por duplicado
			// (QoS)
			endpoint.publishRelease(message.messageId());
		}
	}

	/**
	 * Manejador encargado de notificar y procesar la desconexión de los clientes.
	 * @param endpoint
	 */
	private static void handleClientDisconnect(MqttEndpoint endpoint) {
		endpoint.disconnectHandler(h -> {
			// Eliminamos al cliente de todos los topics a los que estaba suscritos
			Stream.of(clientTopics.keySet())
				.filter(e -> clientTopics.containsEntry(e, endpoint))
				.forEach(s -> clientTopics.remove(s, endpoint));
			System.out.println("El cliente remoto se ha desconectado [" + endpoint.clientIdentifier() + "]");
		});
	}




	// Parar el servicio http
	public void stop() {
		if (server != null)
			server.close();

	}

	/****************
	 * API REST *
	 ****************/

	// Obtiene la tabla entera especificada en el parametro :tabla
	public void getTablaEntera(RoutingContext routingContext) {
		// Recogemos el valor del parametro
		String tabla = routingContext.request().getParam("tabla");

		if (tabla != null) {
			try {
				// Nos conectamos a la base de datos
				databaseClient.getConnection(comm -> {

					if (comm.succeeded()) {
						SQLConnection connection = comm.result();
						String query = "SELECT * FROM " + tabla; // La
						// interrogacion
						// es una
						// variable,
						// y van por
						// orden al
						// identificarlas

						// JsonArray paramQuery = new JsonArray().add(tabla);
						// connection.queryWithParams(query, paramQuery, res -> {
						connection.query(query, res -> {

							if (res.succeeded()) {

								routingContext.response().setStatusCode(200)
										.end(Json.encodePrettily(res.result().getRows()));

							} else {
								routingContext.response().setStatusCode(400).end(res.cause().toString());
							}
						});
						connection.close();
					} else {
						routingContext.response().setStatusCode(400).end(comm.cause().toString());
					}

				});

				// routingContext.response().setStatusCode(200).end(Json.encodePrettily(database.get(param)));
			} catch (ClassCastException e) {
				routingContext.response().setStatusCode(400).end();
			}
		} else {
			routingContext.response().setStatusCode(400).end();
		}

	}

	// Obtiene las mediciones entre las fechas especificadas en el siguiente
	// formato: dd:MM:aaaa de la tabla que indique :tabla
	public void getMedicionEntreFechas(RoutingContext routingContext) throws ParseException {
		// Recogemos el valor del parametro
		String tabla = routingContext.request().getParam("tabla");
		String f1 = routingContext.request().getParam("fecha1");
		String f2 = routingContext.request().getParam("fecha2");
		DateFormat formato = new SimpleDateFormat("dd:MM:yyyy");
		Date fecha1 = formato.parse(f1);
		Date fecha2 = formato.parse(f2);
		Double f1m = fecha1.getTime() / 1000000.0;
		Double f2m = fecha2.getTime() / 1000000.0;

		if (tabla != null) {
			try {
				// Nos conectamos a la base de datos
				databaseClient.getConnection(comm -> {

					if (comm.succeeded()) {
						SQLConnection connection = comm.result();
						String query = "SELECT medicion FROM " + tabla + " WHERE fecha < " + f2m + " AND fecha > " + f1m
								+ ";"; // La
						// interrogacion
						// es una
						// variable,
						// y van por
						// orden al
						// identificarlas

						JsonArray paramQuery = new JsonArray().add(tabla);
						// connection.queryWithParams(query, paramQuery, res -> {
						connection.query(query, res -> {

							if (res.succeeded()) {

								routingContext.response().setStatusCode(200)
										.end(Json.encodePrettily(res.result().getRows()));

							} else {
								routingContext.response().setStatusCode(400).end(res.cause().toString());
							}
						});
						connection.close();
					} else {
						routingContext.response().setStatusCode(400).end(comm.cause().toString());
					}

				});

				// routingContext.response().setStatusCode(200).end(Json.encodePrettily(database.get(param)));
			} catch (ClassCastException e) {
				routingContext.response().setStatusCode(400).end();
			}
		} else {
			routingContext.response().setStatusCode(400).end();
		}

	}

	// Obtiene la media total de todas las mediciones de la tabla que sea (:tabla)
	public void getMediaTotal(RoutingContext routingContext) {
		// Recogemos el valor del parametro
		String tabla = routingContext.request().getParam("tabla");

		if (tabla != null) {
			try {
				// Nos conectamos a la base de datos
				databaseClient.getConnection(comm -> {

					if (comm.succeeded()) {
						SQLConnection connection = comm.result();
						String query = "SELECT medicion FROM " + tabla;
						// JsonArray paramQuery = new JsonArray().add(tabla);
						// connection.queryWithParams(query, paramQuery, res -> {
						connection.query(query, res -> {

							if (res.succeeded()) {

							//	String datos = Json.encode(res.result().getRows());
								Double medicion = 0.0;
								for (JsonObject med : res.result().getRows()) {
									medicion += med.getDouble("medicion");
								}

								routingContext.response().setStatusCode(200)
										.end("Media = " + (medicion / res.result().getRows().size()));

							} else {
								routingContext.response().setStatusCode(400).end(res.cause().toString());
							}

						});
						connection.close();
					} else {
						routingContext.response().setStatusCode(400).end(comm.cause().toString());
					}

				});

				// routingContext.response().setStatusCode(200).end(Json.encodePrettily(database.get(param)));
			} catch (ClassCastException e) {
				routingContext.response().setStatusCode(400).end();
			}
		} else {
			routingContext.response().setStatusCode(400).end();
		}

	}

	
	public void getUltimaMedicion (RoutingContext routingContext) {
		// Recogemos el valor del parametro
		String tabla = routingContext.request().getParam("tabla");

		if (tabla != null) {
			try {
				// Nos conectamos a la base de datos
				databaseClient.getConnection(comm -> {

					if (comm.succeeded()) {
						SQLConnection connection = comm.result();
						String query = "SELECT medicion FROM " + tabla + " ORDER BY indice DESC LIMIT 1";
						// JsonArray paramQuery = new JsonArray().add(tabla);
						// connection.queryWithParams(query, paramQuery, res -> {
						connection.query(query, res -> {

							if (res.succeeded()) {

							//	String datos = Json.encode(res.result().getRows());
								List<JsonObject> jsons = res.result().getRows();
								Double medicion = jsons.get(0).getDouble("medicion");

								routingContext.response().setStatusCode(200)
										.end(medicion.toString());

							} else {
								routingContext.response().setStatusCode(400).end(res.cause().toString());
							}

						});
						connection.close();
					} else {
						routingContext.response().setStatusCode(400).end(comm.cause().toString());
					}

				});

				// routingContext.response().setStatusCode(200).end(Json.encodePrettily(database.get(param)));
			} catch (ClassCastException e) {
				routingContext.response().setStatusCode(400).end();
			}
		} else {
			routingContext.response().setStatusCode(400).end();
		}

	}

	
	// Inserta una medicion (:medicion) en una tabla existente (:tabla) del sensor
	// con id que sea (:id)
	public void putMed(RoutingContext routingContext) {

		String tabla = routingContext.request().getParam("tabla");

		Buffer payload = routingContext.getBody();
		Medicion med = Json.decodeValue(payload, Medicion.class);

		if (
				
				((tabla.equals("humedad_suelo")|| tabla.equals("humedad_aire")) && ((med.getMedicion() > 100f) || (med.getMedicion() < 0f)))) {
			routingContext.response().setStatusCode(400)
					.end("Parámetro incorrecto: La humedad relativa debe estar entre 0 y 100");
		} else {

			// id no nos hace falta ahora mismo, es para cuando tenemos mas de una
			// instalacion a la vez
			// String id = routingContext.request().getParam("a2");

			// if (medicion != null && id != null){
			if (med.getMedicion() != null) {
				try {
					// Nos conectamos a la base de datos
					databaseClient.getConnection(comm -> {

						// medicionstr = routingContext.request().getParam("a1");
						if (comm.succeeded()) {
							SQLConnection connection = comm.result();

							Double fecha = (Calendar.getInstance().getTimeInMillis()) / 1000000.0;
							// Dividimos entre 1000000 el resultado para no quedarnos fuera del rango
							// A la hora del tratamiento, habra que multiplicar por 1000000 <--------- ¿es
							// necesario realmente?

							String query = "INSERT INTO " + tabla + "(medicion,fecha,iddisp) VALUES( "
									+ med.getMedicion() + " , " + fecha + "," + med.getIddisp() + ");";

							// JsonArray paramQuery = new JsonArray().add(medicionstr);
							// paramQuery = new JsonArray().add(fecha);

							// connection.queryWithParams(query, paramQuery, res -> {

							connection.query(query, res -> {
								if (res.succeeded()) {

									routingContext.response().setStatusCode(200).end("Insercion realizada");

								} else {
									routingContext.response().setStatusCode(400).end(res.cause().toString());
								}
							});
							connection.close();
						} else {
							routingContext.response().setStatusCode(400).end(comm.cause().toString());
						}

					});

				} catch (ClassCastException e) {
					routingContext.response().setStatusCode(400).end();
				}
			} else {
				routingContext.response().setStatusCode(400).end("Parámetro Incorrecto");
			}
		}
	}

	public void postComando(RoutingContext routingContext) {

		// Primero comando: que escriba el esp algo por pantalla
		// Si esto funciona, se puede hacer casi cualquier cosa
		// Distintos comandos: encender (servo)motores, encender un led (termina siendo
		// escribir x en el puerto y) <-- hay que implementarlo en el nodemcus
		
		Buffer payload = routingContext.getBody();

		Comando comando = (Comando)Json.decodeValue(payload, Comando.class);

		mqttCliente.publish("node", Buffer.buffer(comando.comando), MqttQoS.AT_LEAST_ONCE, false, true);

		routingContext.response().setStatusCode(200).end("Comando recibido");
	}

}

/*
 * 
 * 
 * Probar MQTT: hay que hacer un router.post que reciba un comando (predefinido)
 * y que haga que el ESP8266 reaccione Habria que hacer 1 .post por comando, o
 * hacer uno solo y tratar el comando interiormente (mejor 1 por cada creo)
 * 
 */