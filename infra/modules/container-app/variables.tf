variable "name" {
  description = "Container app name, which is also the FQDN label. Lowercase alphanumeric and hyphens, 32 characters maximum."
  type        = string
}

variable "resource_group_name" {
  type = string
}

variable "container_app_environment_id" {
  type = string
}

variable "identity_id" {
  description = "Resource id of the service's user-assigned identity. Used for both the registry pull and Key Vault secret resolution."
  type        = string
}

variable "registry_server" {
  type = string
}

variable "image" {
  description = "Fully qualified image reference. Only ever used on create — see the lifecycle block."
  type        = string
}

variable "target_port" {
  type = number
}

variable "external_ingress" {
  description = "True for gateway and identity, which the browser addresses directly (ADR 0006)."
  type        = bool
}

variable "env_vars" {
  description = "Plain environment variables."
  type        = map(string)
  default     = {}
}

variable "secret_env_vars" {
  description = "Environment variable name to container-app secret name."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Container-app secret name to a versionless Key Vault secret id."
  type        = map(string)
  default     = {}
}

variable "cpu" {
  description = "Must be exactly half the memory in Gi, or the API rejects the revision."
  type        = number
  default     = 0.5
}

variable "memory" {
  type    = string
  default = "1Gi"
}

variable "min_replicas" {
  type    = number
  default = 0
}

variable "max_replicas" {
  description = "Must stay 1 for gateway and notification, which hold in-memory state (ADR 0011)."
  type        = number
  default     = 1
}

variable "concurrent_requests" {
  description = "Concurrency for the HTTP scale rule, which is what lets an open SSE stream hold a replica."
  type        = string
  default     = "10"
}

variable "revision_suffix" {
  description = "Only ever used on create; the deploy pipeline owns revision suffixes afterwards."
  type        = string
  default     = "tf-initial"
}

variable "tags" {
  type    = map(string)
  default = {}
}
