
#define HTTP_DEBUG
#include "./propio/propio.hpp"
#include "./mqtt/mqtt.hpp"
#include "./rest/rest.hpp"

char* ssid = (char*)"yourwifissid";
char* pass = (char*)"yourpass";
char* ip = (char*)"217.216.241.223";
const int mqttPrt = 30654;
const int httpPrt = 25489;

byte temperatura = 0;
byte humedad_aire = 0;
byte data[40] = {0};
int humedad_suelo;
String response = "";


AsyncMqttClient clienteMqtt;
RestClient clienteRest(ip,httpPrt);

int idsensor = 1; //Identificador del dispositivo
char control_riego = 0;//Variable de control del riego -- 1:Riego activado; 0:Riego desactivado
int pinDHT11 = D0; //Temperatura y Humedad aire D0
const int pinFC28 = A0; //Humedad del suelo A0
const int pinServo = D1; //D1
bool suscrito = false;


Servo mi_servo;


void setup() {

  mi_servo.attach(pinServo);

//Nos conectamos a la red deseada
Serial.begin(9600);

  conectar_wifi(ssid,pass);

  conectar_mqtt(ip,mqttPrt);

  suscribirse((char*)"node");

Serial.println("setup finalizado");


}


void loop() {
SimpleDHT11 dht11;
  // Inicio
  Serial.println("=================================");
  Serial.println("Muestra de sensores...");

 // Lee los datos de los sensores.
  int humedad_suelo = map(analogRead(pinFC28),0,1023,100,0);
  byte temperatura = 0;
  byte humedad_aire = 0;
  byte data[40] = {0};

  //Temperatura y humedad del aire

    if (dht11.read(pinDHT11, &temperatura, &humedad_aire, data)) {
    Serial.print("Error en la lectura  del DHT11");
    return;
  }

  if(humedad_suelo < 50){

    mi_servo.writeMicroseconds(1500);

    Serial.println("Humedad en suelo baja. REGANDO...");

  }
  else{

    mi_servo.writeMicroseconds(2500);


  }

  Serial.print("Muestreo temperatura y humedad del aire: ");
  Serial.print((int)temperatura); Serial.print(" *C, ");
  Serial.print((int)humedad_aire); Serial.println(" %");
  Serial.print("Muestreo humedad del suelo: ");
  Serial.print(humedad_suelo); Serial.println(" %");
  Serial.println(insertar_medicion("temperatura", (float) temperatura));
  delay(1000);
  Serial.println(insertar_medicion("humedad_aire", (float)humedad_aire));
  delay(1000);
  Serial.println(insertar_medicion("humedad_suelo", (float)humedad_suelo));
  delay(5000);


}
