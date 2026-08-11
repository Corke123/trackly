resource "azurerm_postgresql_flexible_server_database" "db" {
  for_each  = local.databases
  name      = each.value
  server_id = var.postgres_server_id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "container_apps" {
  name             = "allow-cae-${var.env_name}"
  server_id        = var.postgres_server_id
  start_ip_address = azurerm_container_app_environment.this.static_ip_address
  end_ip_address   = azurerm_container_app_environment.this.static_ip_address
}
