#include <ESP8266Wifi.h>
#include <SimpleDHT.h>
#include <Servo.h>

int conectar_wifi(char* ssid,char* pass );

void leer_temp(byte* temperatura,byte* humedad_aire, byte* data);

void leer_humedad_suelo();

void activar_riego(Servo* mi_servo);

void desactivar_riego(Servo* mi_servo);
