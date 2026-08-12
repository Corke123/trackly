resource "azurerm_servicebus_namespace" "shared" {
  name                = "sb-trackly-${random_string.suffix.result}"
  resource_group_name = azurerm_resource_group.shared.name
  location            = azurerm_resource_group.shared.location
  sku                 = "Standard"
  local_auth_enabled  = false
  tags                = var.tags
}
