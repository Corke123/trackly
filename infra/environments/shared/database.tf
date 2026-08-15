
resource "random_password" "postgres" {
  length           = 32
  min_lower        = 4
  min_upper        = 4
  min_numeric      = 4
  min_special      = 4
  override_special = "!#%*()-_=+[]{}<>:?"
}

resource "azurerm_postgresql_flexible_server" "shared" {
  name                = "psql-trackly-${random_string.suffix.result}"
  resource_group_name = azurerm_resource_group.shared.name
  location            = azurerm_resource_group.shared.location

  version                      = "17"
  sku_name                     = "B_Standard_B1ms"
  storage_mb                   = var.postgres_storage_mb
  storage_tier                 = "P4"
  auto_grow_enabled            = false
  backup_retention_days        = 7
  geo_redundant_backup_enabled = false

  administrator_login    = "trackly"
  administrator_password = random_password.postgres.result

  authentication {
    active_directory_auth_enabled = true
    password_auth_enabled         = true
    tenant_id                     = data.azurerm_client_config.current.tenant_id
  }

  lifecycle {
    ignore_changes = [zone, high_availability]
  }

  tags = var.tags
}

resource "azurerm_postgresql_flexible_server_configuration" "require_secure_transport" {
  name      = "require_secure_transport"
  server_id = azurerm_postgresql_flexible_server.shared.id
  value     = "ON"
}

resource "azurerm_postgresql_flexible_server_configuration" "minimum_tls" {
  name      = "ssl_min_protocol_version"
  server_id = azurerm_postgresql_flexible_server.shared.id
  value     = "TLSv1.2"
}

resource "azurerm_postgresql_flexible_server_active_directory_administrator" "operator" {
  server_name         = azurerm_postgresql_flexible_server.shared.name
  resource_group_name = azurerm_resource_group.shared.name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  object_id           = var.OPERATOR_OBJECT_ID
  principal_name      = var.OPERATOR_OBJECT_ID
  principal_type      = "User"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "allow-azure-services"
  server_id        = azurerm_postgresql_flexible_server.shared.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "operator" {
  name             = "allow-operator"
  server_id        = azurerm_postgresql_flexible_server.shared.id
  start_ip_address = var.OPERATOR_IP
  end_ip_address   = var.OPERATOR_IP
}
