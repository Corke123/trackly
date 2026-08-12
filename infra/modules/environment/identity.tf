resource "azurerm_user_assigned_identity" "app" {
  for_each            = toset(local.services)
  name                = "id-trackly-${each.key}-${var.env_name}"
  resource_group_name = azurerm_resource_group.env.name
  location            = azurerm_resource_group.env.location
  tags                = var.tags
}
