data "terraform_remote_state" "shared" {
  backend = "azurerm"

  config = {
    resource_group_name  = var.TFSTATE_RESOURCE_GROUP
    storage_account_name = var.TFSTATE_STORAGE_ACCOUNT
    container_name       = var.tfstate_container
    key                  = "shared.tfstate"
    use_azuread_auth     = true
  }
}

module "environment" {
  source = "../../modules/environment"

  env_name  = "staging"
  location  = var.location
  image_tag = var.image_tag

  client_secret        = var.CLIENT_SECRET
  client_secret_bcrypt = var.CLIENT_SECRET_BCRYPT

  acr_id                               = data.terraform_remote_state.shared.outputs.acr_id
  acr_login_server                     = data.terraform_remote_state.shared.outputs.acr_login_server
  log_analytics_workspace_id           = data.terraform_remote_state.shared.outputs.log_analytics_workspace_id
  postgres_server_id                   = data.terraform_remote_state.shared.outputs.postgres_server_id
  postgres_fqdn                        = data.terraform_remote_state.shared.outputs.postgres_fqdn
  postgres_admin_password              = data.terraform_remote_state.shared.outputs.postgres_admin_password
  servicebus_namespace_id              = data.terraform_remote_state.shared.outputs.servicebus_namespace_id
  servicebus_fully_qualified_namespace = data.terraform_remote_state.shared.outputs.servicebus_fully_qualified_namespace

  tags = merge(var.tags, { environment = "staging" })
}
