data "azurerm_client_config" "current" {}

output "resource_group_name" {
  value = azurerm_resource_group.shared.name
}

output "location" {
  value = azurerm_resource_group.shared.location
}

output "acr_id" {
  value = azurerm_container_registry.shared.id
}

output "acr_name" {
  description = "Set as the ACR_NAME repository variable in GitHub."
  value       = azurerm_container_registry.shared.name
}

output "acr_login_server" {
  description = "Set as the ACR_LOGIN_SERVER repository variable in GitHub."
  value       = azurerm_container_registry.shared.login_server
}

output "log_analytics_workspace_id" {
  value = azurerm_log_analytics_workspace.shared.id
}

output "postgres_server_id" {
  value = azurerm_postgresql_flexible_server.shared.id
}

output "postgres_server_name" {
  value = azurerm_postgresql_flexible_server.shared.name
}

output "postgres_fqdn" {
  value = azurerm_postgresql_flexible_server.shared.fqdn
}

output "postgres_admin_username" {
  value = azurerm_postgresql_flexible_server.shared.administrator_login
}

output "postgres_admin_password" {
  description = "Break-glass only. No application is wired to it (ADR 0013)."
  value       = random_password.postgres.result
  sensitive   = true
}

output "servicebus_namespace_id" {
  value = azurerm_servicebus_namespace.shared.id
}

output "servicebus_namespace_name" {
  value = azurerm_servicebus_namespace.shared.name
}

output "servicebus_fully_qualified_namespace" {
  description = "What board and notification receive as SERVICEBUS_NAMESPACE."
  value       = "${azurerm_servicebus_namespace.shared.name}.servicebus.windows.net"
}
