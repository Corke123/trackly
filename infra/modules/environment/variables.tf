variable "env_name" {
  description = "Environment name. Appears in resource names, database names, the topic name, and every URL."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{2,11}$", var.env_name))
    error_message = "env_name must be lowercase alphanumeric, 3-12 characters, so container app names stay within 32 characters."
  }
}

variable "location" {
  type = string
}

variable "image_tag" {
  description = "Tag used only when a container app is first created; the deploy pipeline owns it afterwards."
  type        = string
  default     = "latest"
}

variable "bootstrap_resource_group_name" {
  description = "Resource group holding the GitHub federated identities created by bootstrap.sh."
  type        = string
  default     = "rg-trackly-bootstrap"
}

variable "acr_id" {
  type = string
}

variable "acr_login_server" {
  type = string
}

variable "log_analytics_workspace_id" {
  type = string
}

variable "postgres_server_id" {
  type = string
}

variable "postgres_fqdn" {
  type = string
}

variable "client_secret" {
  description = "Plaintext OAuth2 client secret the gateway authenticates with."
  type        = string
  sensitive   = true
}

variable "client_secret_bcrypt" {
  description = "The same secret as a {bcrypt} hash, which is what identity-service stores. Generate with `htpasswd -bnBC 10 \"\" <secret>`."
  type        = string
  sensitive   = true
}

variable "postgres_admin_password" {
  description = "Stored in this environment's Key Vault for break-glass use. No app reads it."
  type        = string
  sensitive   = true
}

variable "servicebus_namespace_id" {
  type = string
}

variable "servicebus_fully_qualified_namespace" {
  type = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
