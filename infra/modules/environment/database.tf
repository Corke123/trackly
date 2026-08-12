resource "azurerm_postgresql_flexible_server_database" "db" {
  for_each  = local.databases
  name      = each.value
  server_id = var.postgres_server_id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

