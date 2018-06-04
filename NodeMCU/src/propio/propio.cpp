#include "propio.hpp"


extern int idsensor;
extern int control_riego;
extern SimpleDHT11 dht11;
extern int pinDHT11;

int conectar_wifi(char* ssid, char* pass){
  WiFi.mode(WIFI_AP); // Modo cliente WiFi

  WiFi.begin(ssid, pass);

  Serial.print("Conectando");
  while (WiFi.status() != WL_CONNECTED)
  {
    delay(500);
    Serial.print(".");
  }
  Serial.println();

  Serial.print("Dispositivo: ");Serial.println(idsensor);
  Serial.print("Conectado, Dirección IP: ");
  Serial.println(WiFi.localIP());

  return WiFi.status();
}

void leer_temp(byte* temperatura,byte* humedad_aire, byte* data){

  if (dht11.read(pinDHT11, temperatura, humedad_aire, data)) {
    Serial.print("Error en la lectura  del DHT11");
    return;
  }
}

void activar_riego(Servo* mi_servo){
  control_riego = 1;
	mi_servo->write(0);/*o 180, lo que proceda*/

}

void desactivar_riego(Servo* mi_servo){

	control_riego = 0;
  mi_servo->write(180);/*o 0, lo que proceda*/

}
