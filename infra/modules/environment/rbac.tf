resource "azurerm_role_assignment" "acr_pull" {
  for_each             = azurerm_user_assigned_identity.app
  scope                = var.acr_id
  role_definition_name = "AcrPull"
  principal_id         = each.value.principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "key_vault_secrets" {
  for_each             = toset(["gateway", "identity"])
  scope                = azurerm_key_vault.env.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.app[each.key].principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "key_vault_certificates" {
  scope                = azurerm_key_vault.env.id
  role_definition_name = "Key Vault Certificate User"
  principal_id         = azurerm_user_assigned_identity.app["identity"].principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "servicebus_sender" {
  scope                = azurerm_servicebus_topic.board_events.id
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = azurerm_user_assigned_identity.app["board"].principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "servicebus_receiver" {
  scope                = azurerm_servicebus_topic.board_events.id
  role_definition_name = "Azure Service Bus Data Receiver"
  principal_id         = azurerm_user_assigned_identity.app["notification"].principal_id
  principal_type       = "ServicePrincipal"
}

data "azurerm_user_assigned_identity" "deployer" {
  name                = "id-trackly-github-${var.env_name}"
  resource_group_name = var.bootstrap_resource_group_name
}

resource "azurerm_role_assignment" "deployer_contributor" {
  scope                = azurerm_resource_group.env.id
  role_definition_name = "Contributor"
  principal_id         = data.azurerm_user_assigned_identity.deployer.principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "deployer_managed_identity_operator" {
  scope                = azurerm_resource_group.env.id
  role_definition_name = "Managed Identity Operator"
  principal_id         = data.azurerm_user_assigned_identity.deployer.principal_id
  principal_type       = "ServicePrincipal"
}

resource "time_sleep" "rbac" {
  create_duration = "180s"

  triggers = {
    assignments = join(",", concat(
      [for r in azurerm_role_assignment.acr_pull : r.id],
      [for r in azurerm_role_assignment.key_vault_secrets : r.id],
      [
        azurerm_role_assignment.key_vault_certificates.id,
        azurerm_role_assignment.servicebus_sender.id,
        azurerm_role_assignment.servicebus_receiver.id,
      ],
    ))
  }
}
