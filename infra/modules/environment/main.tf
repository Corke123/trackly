data "azurerm_client_config" "current" {}

resource "azurerm_resource_group" "env" {
  name     = "rg-trackly-${var.env_name}"
  location = var.location
  tags     = var.tags
}

resource "azurerm_container_app_environment" "this" {
  name                       = "cae-trackly-${var.env_name}"
  resource_group_name        = azurerm_resource_group.env.name
  location                   = azurerm_resource_group.env.location
  log_analytics_workspace_id = var.log_analytics_workspace_id
  logs_destination           = "log-analytics"

  tags = var.tags
}

locals {
  domain = azurerm_container_app_environment.this.default_domain

  app_names = {
    gateway      = "gateway-${var.env_name}"
    identity     = "identity-${var.env_name}"
    board        = "board-${var.env_name}"
    notification = "notification-${var.env_name}"
  }

  gateway_url  = "https://${local.app_names.gateway}.${local.domain}"
  identity_url = "https://${local.app_names.identity}.${local.domain}"

  board_url        = "https://${local.app_names.board}.internal.${local.domain}"
  notification_url = "https://${local.app_names.notification}.internal.${local.domain}"

  topic_name = "board-events-${var.env_name}"

  databases = {
    board        = "board_db_${var.env_name}"
    identity     = "identity_db_${var.env_name}"
    notification = "notification_db_${var.env_name}"
  }

  services = ["gateway", "identity", "board", "notification"]

  jdbc_auth_plugin = "com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin"

  jdbc_urls = {
    for service, database in local.databases :
    service => "jdbc:postgresql://${var.postgres_fqdn}:5432/${database}?sslmode=require&authenticationPluginClassName=${local.jdbc_auth_plugin}"
  }

  common_env = {
    TRACKLY_ISSUER_URI                         = local.identity_url
    JAVA_TOOL_OPTIONS                          = "-XX:MaxRAMPercentage=75"
    SERVER_FORWARD_HEADERS_STRATEGY            = "framework"
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "3"
  }
}
