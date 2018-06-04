#include "mqtt.hpp"
#include "../propio/propio.hpp"

extern AsyncMqttClient clienteMqtt;
extern bool suscrito;
extern int idsensor;
extern Servo mi_servo;
extern int humedad_suelo;

void onMqttMessage(char* topic, char* payload, AsyncMqttClientMessageProperties properties, size_t len, size_t index, size_t total) {
char i;
  //Tenemos que guardar los valores,
  //si se recibe otro comando los valores de
  //topic y payload se sobreescribirán


  char* carga = payload;
  char comando = carga[0];


  switch(comando){

    //Comando == 1 -> Activamos el riego 1500--> 0º
    case '1': mi_servo.writeMicroseconds(1500);Serial.println("Riego activado");break;


    //Comando == 2 -> Desactivamos el riego 2000->90º
    case '2': mi_servo.writeMicroseconds(2500);Serial.println("Riego desactivado");break;

    case '3':break;

    case '4':humedad_suelo = 0;break;
    //Si el comando recibido no se contempla aqui, no es válido
    default:Serial.println("Comando no válido");break;

  }

  Serial.print("Comando recibido: ");

  for (i = 0; i < len ; i++)
    Serial.printf("%c",carga[i]);

    Serial.println("");
  }

void onMqttSubscribe(uint16_t packetId, uint8_t qos) {
  suscrito = true;
}

bool suscribirse(char* topic){

    while(!suscrito){
      clienteMqtt.subscribe(topic,idsensor);
      Serial.print(".");
      delay(3500);
      };

    Serial.print("Suscrito al topic ");Serial.println(topic);

    return suscrito;
    }

int conectar_mqtt(char* ip, int puerto){
  clienteMqtt.setServer(ip,puerto);
  clienteMqtt.onMessage(onMqttMessage);
  clienteMqtt.onSubscribe(onMqttSubscribe);

  Serial.println("Conectando al servidor MQTT");
  while(!clienteMqtt.connected()){
    clienteMqtt.connect();
    delay(1000);
    Serial.print(".");

    };
  Serial.print("Conectado al servidor MQTT con IP: ");Serial.println(ip);
  Serial.print("Puerto: ");Serial.println(puerto);

return clienteMqtt.connected();

}
