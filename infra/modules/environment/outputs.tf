output "resource_group_name" {
  value = azurerm_resource_group.env.name
}

output "gateway_url" {
  description = "The application's public entry point, and the OAuth2 redirect URI's origin."
  value       = local.gateway_url
}

output "identity_url" {
  description = "The OAuth2 issuer. Identical for the authorization server and all three validators."
  value       = local.identity_url
}

output "container_app_environment_static_ip" {
  description = "What the PostgreSQL firewall rule is pinned to."
  value       = azurerm_container_app_environment.this.static_ip_address
}

output "container_app_names" {
  description = "Consumed by the deploy workflow to address revisions."
  value       = local.app_names
}

output "key_vault_name" {
  value = azurerm_key_vault.env.name
}

output "database_names" {
  description = "Passed to grant-db-identities.sh."
  value       = local.databases
}

output "app_identity_names" {
  description = "The database principals grant-db-identities.sh must create."
  value       = { for k, v in azurerm_user_assigned_identity.app : k => v.name }
}
