resource "azurerm_servicebus_topic" "board_events" {
  name                         = local.topic_name
  namespace_id                 = var.servicebus_namespace_id
  default_message_ttl          = "P7D"
  requires_duplicate_detection = false
  partitioning_enabled         = false
  max_size_in_megabytes        = 1024
}

resource "azurerm_servicebus_subscription" "notification" {
  name                                 = "notification"
  topic_id                             = azurerm_servicebus_topic.board_events.id
  lock_duration                        = "PT1M"
  max_delivery_count                   = 10
  default_message_ttl                  = "P7D"
  dead_lettering_on_message_expiration = false
  requires_session                     = false
}
