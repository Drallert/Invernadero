#include <AsyncMqttClient.h>

void onMqttMessage(char* topic, char* payload, AsyncMqttClientMessageProperties properties, size_t len, size_t index, size_t total);
void onMqttSubscribe(uint16_t packetId, uint8_t qos);
bool suscribirse(char* topic);
int conectar_mqtt(char* ip, int puerto);
