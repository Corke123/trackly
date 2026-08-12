
module "identity_app" {
  source = "../container-app"

  name                         = local.app_names.identity
  resource_group_name          = azurerm_resource_group.env.name
  container_app_environment_id = azurerm_container_app_environment.this.id
  identity_id                  = azurerm_user_assigned_identity.app["identity"].id
  registry_server              = var.acr_login_server
  image                        = "${var.acr_login_server}/trackly/identity-service:${var.image_tag}"
  target_port                  = 9000
  external_ingress             = true

  env_vars = merge(local.common_env, {
    SERVER_PORT          = "9000"
    IDENTITY_DB_URL      = local.jdbc_urls.identity
    IDENTITY_DB_USERNAME = azurerm_user_assigned_identity.app["identity"].name

    TRACKLY_CLIENT_ID                = "trackly"
    TRACKLY_REDIRECT_URI             = "${local.gateway_url}/login/oauth2/code/trackly"
    TRACKLY_POST_LOGOUT_REDIRECT_URI = local.gateway_url

    TRACKLY_JWT_SIGNING_SOURCE   = "keyvault"
    TRACKLY_KEYVAULT_URI         = azurerm_key_vault.env.vault_uri
    TRACKLY_JWT_CERTIFICATE_NAME = azurerm_key_vault_certificate.jwt_signing.name

    SERVER_SERVLET_SESSION_COOKIE_SECURE = "true"

    AZURE_CLIENT_ID = azurerm_user_assigned_identity.app["identity"].client_id
  })

  secrets         = { "trackly-client-secret-hash" = azurerm_key_vault_secret.client_secret_hash.versionless_id }
  secret_env_vars = { TRACKLY_CLIENT_SECRET = "trackly-client-secret-hash" }

  tags = var.tags

  depends_on = [
    time_sleep.rbac,
    azurerm_postgresql_flexible_server_database.db,
    azurerm_postgresql_flexible_server_firewall_rule.container_apps,
  ]
}

module "board_app" {
  source = "../container-app"

  name                         = local.app_names.board
  resource_group_name          = azurerm_resource_group.env.name
  container_app_environment_id = azurerm_container_app_environment.this.id
  identity_id                  = azurerm_user_assigned_identity.app["board"].id
  registry_server              = var.acr_login_server
  image                        = "${var.acr_login_server}/trackly/board-service:${var.image_tag}"
  target_port                  = 8081
  external_ingress             = false

  env_vars = merge(local.common_env, {
    SERVER_PORT       = "8081"
    BOARD_DB_URL      = local.jdbc_urls.board
    BOARD_DB_USERNAME = azurerm_user_assigned_identity.app["board"].name

    SERVICEBUS_NAMESPACE = var.servicebus_fully_qualified_namespace
    SERVICEBUS_TOPIC     = azurerm_servicebus_topic.board_events.name

    AZURE_CLIENT_ID = azurerm_user_assigned_identity.app["board"].client_id
  })

  tags = var.tags

  depends_on = [
    time_sleep.rbac,
    azurerm_postgresql_flexible_server_database.db,
    azurerm_postgresql_flexible_server_firewall_rule.container_apps,
  ]
}

module "notification_app" {
  source = "../container-app"

  name                         = local.app_names.notification
  resource_group_name          = azurerm_resource_group.env.name
  container_app_environment_id = azurerm_container_app_environment.this.id
  identity_id                  = azurerm_user_assigned_identity.app["notification"].id
  registry_server              = var.acr_login_server
  image                        = "${var.acr_login_server}/trackly/notification-service:${var.image_tag}"
  target_port                  = 8082
  external_ingress             = false
  max_replicas                 = 1

  env_vars = merge(local.common_env, {
    SERVER_PORT              = "8082"
    NOTIFICATION_DB_URL      = local.jdbc_urls.notification
    NOTIFICATION_DB_USERNAME = azurerm_user_assigned_identity.app["notification"].name

    SERVICEBUS_NAMESPACE    = var.servicebus_fully_qualified_namespace
    SERVICEBUS_ENABLED      = "true"
    SERVICEBUS_TOPIC        = azurerm_servicebus_topic.board_events.name
    SERVICEBUS_SUBSCRIPTION = azurerm_servicebus_subscription.notification.name

    ACTIVITY_STREAM_HEARTBEAT_DELAY_MS = "25000"
    ACTIVITY_STREAM_TIMEOUT            = "10m"
    ACTIVITY_STREAM_REPLAY_LIMIT       = "20"

    AZURE_CLIENT_ID = azurerm_user_assigned_identity.app["notification"].client_id
  })

  tags = var.tags

  depends_on = [
    time_sleep.rbac,
    azurerm_postgresql_flexible_server_database.db,
    azurerm_postgresql_flexible_server_firewall_rule.container_apps,
  ]
}

module "gateway_app" {
  source = "../container-app"

  name                         = local.app_names.gateway
  resource_group_name          = azurerm_resource_group.env.name
  container_app_environment_id = azurerm_container_app_environment.this.id
  identity_id                  = azurerm_user_assigned_identity.app["gateway"].id
  registry_server              = var.acr_login_server
  image                        = "${var.acr_login_server}/trackly/gateway-service:${var.image_tag}"
  target_port                  = 8080
  external_ingress             = true
  max_replicas                 = 1

  env_vars = merge(local.common_env, {
    SERVER_PORT           = "8080"
    TRACKLY_SERVE_SPA     = "true"
    SESSION_COOKIE_SECURE = "true"
    TRACKLY_CLIENT_ID     = "trackly"

    BOARD_SERVICE_URI        = local.board_url
    NOTIFICATION_SERVICE_URI = local.notification_url
    IDENTITY_SERVICE_URI     = local.identity_url
  })

  secrets         = { "trackly-client-secret" = azurerm_key_vault_secret.client_secret.versionless_id }
  secret_env_vars = { TRACKLY_CLIENT_SECRET = "trackly-client-secret" }

  tags = var.tags

  depends_on = [
    time_sleep.rbac,
    module.identity_app,
  ]
}
