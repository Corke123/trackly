output "gateway_url" {
  description = "The production entry point."
  value       = module.environment.gateway_url
}

output "identity_url" {
  value = module.environment.identity_url
}

output "resource_group_name" {
  value = module.environment.resource_group_name
}

output "container_app_names" {
  value = module.environment.container_app_names
}

output "database_names" {
  value = module.environment.database_names
}

output "app_identity_names" {
  value = module.environment.app_identity_names
}
