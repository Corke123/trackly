
resource "azurerm_container_app" "this" {
  name                         = var.name
  resource_group_name          = var.resource_group_name
  container_app_environment_id = var.container_app_environment_id

  revision_mode          = "Multiple"
  max_inactive_revisions = 5

  identity {
    type         = "UserAssigned"
    identity_ids = [var.identity_id]
  }

  registry {
    server   = var.registry_server
    identity = var.identity_id
  }

  dynamic "secret" {
    for_each = var.secrets
    content {
      name                = secret.key
      key_vault_secret_id = secret.value
      identity            = var.identity_id
    }
  }

  ingress {
    external_enabled           = var.external_ingress
    target_port                = var.target_port
    transport                  = "auto"
    allow_insecure_connections = false

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas    = var.min_replicas
    max_replicas    = var.max_replicas
    revision_suffix = var.revision_suffix

    termination_grace_period_seconds = 30

    container {
      name   = var.name
      image  = var.image
      cpu    = var.cpu
      memory = var.memory

      dynamic "env" {
        for_each = var.env_vars
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = var.secret_env_vars
        content {
          name        = env.key
          secret_name = env.value
        }
      }

      startup_probe {
        transport               = "HTTP"
        port                    = var.target_port
        path                    = "/actuator/health/readiness"
        interval_seconds        = 10
        failure_count_threshold = 30
      }

      readiness_probe {
        transport               = "HTTP"
        port                    = var.target_port
        path                    = "/actuator/health/readiness"
        interval_seconds        = 10
        failure_count_threshold = 3
      }

      liveness_probe {
        transport               = "HTTP"
        port                    = var.target_port
        path                    = "/actuator/health/liveness"
        initial_delay           = 60
        interval_seconds        = 30
        failure_count_threshold = 3
      }
    }

    http_scale_rule {
      name                = "http"
      concurrent_requests = var.concurrent_requests
    }
  }

  lifecycle {
    ignore_changes = [
      template[0].container[0].image,
      template[0].revision_suffix,
      ingress[0].traffic_weight,
      secret,
    ]
  }

  tags = var.tags
}
