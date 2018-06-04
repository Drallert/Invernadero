#include "rest.hpp"
#include <ArduinoJson.h>

extern RestClient clienteRest;
extern int idsensor;


String insertar_medicion(char* tabla, float medicion){

  StaticJsonBuffer<50> jsonBuffer;
  String response = "";

  JsonObject& bodyJson = jsonBuffer.createObject();
  String body ="";
  const char* pbody;

  const char* purl;
  String url = "/database/insercion/";
  url += tabla;
  url +="/";

  bodyJson["medicion"] = medicion;
  bodyJson["iddisp"] = idsensor;

  bodyJson.printTo(body);
  pbody = body.c_str();
  purl = url.c_str();

  clienteRest.setContentType("application/json");
  clienteRest.put(purl ,pbody,&response);

//  jsonBuffer.clear();
  return response;

}
