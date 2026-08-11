resource "random_string" "kv" {
  length  = 6
  special = false
  upper   = false
  numeric = true
}

resource "azurerm_key_vault" "env" {
  name                = "kv-trackly-${substr(var.env_name, 0, 4)}-${random_string.kv.result}"
  resource_group_name = azurerm_resource_group.env.name
  location            = azurerm_resource_group.env.location
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name            = "standard"

  rbac_authorization_enabled    = true
  soft_delete_retention_days    = 7
  purge_protection_enabled      = false
  public_network_access_enabled = true

  tags = var.tags
}

resource "azurerm_key_vault_secret" "client_secret" {
  name         = "trackly-client-secret"
  value        = var.client_secret
  key_vault_id = azurerm_key_vault.env.id
}

resource "azurerm_key_vault_secret" "client_secret_hash" {
  name         = "trackly-client-secret-hash"
  value        = var.client_secret_bcrypt
  key_vault_id = azurerm_key_vault.env.id
}

resource "azurerm_key_vault_secret" "postgres_admin_password" {
  name         = "postgres-admin-password"
  value        = var.postgres_admin_password
  key_vault_id = azurerm_key_vault.env.id
}

resource "azurerm_key_vault_certificate" "jwt_signing" {
  name         = "trackly-jwt-signing"
  key_vault_id = azurerm_key_vault.env.id

  certificate_policy {
    issuer_parameters {
      name = "Self"
    }

    key_properties {
      exportable = true
      key_type   = "RSA"
      key_size   = 2048
      reuse_key  = false
    }

    secret_properties {
      content_type = "application/x-pkcs12"
    }

    lifetime_action {
      action {
        action_type = "AutoRenew"
      }
      trigger {
        lifetime_percentage = 80
      }
    }

    x509_certificate_properties {
      subject            = "CN=trackly-jwt-signing"
      validity_in_months = 3
      key_usage          = ["digitalSignature", "keyEncipherment"]
    }
  }

  lifecycle {
    ignore_changes = [certificate_policy]
  }

  depends_on = [azurerm_key_vault.env]
}
