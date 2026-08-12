variable "subscription_id" {
  description = "Azure subscription to deploy into."
  type        = string
}

variable "location" {
  description = "Azure region for every shared resource."
  type        = string
  default     = "westeurope"
}

variable "operator_object_id" {
  description = "Entra object id of the operator, made PostgreSQL Entra administrator for grant-db-identities.sh."
  type        = string
}

variable "operator_ip" {
  description = "Public IP the operator runs grant-db-identities.sh from, e.g. from `curl -s ifconfig.me`."
  type        = string
}

variable "github_infra_identity_principal_id" {
  description = "Principal id of id-trackly-github-infra, created by bootstrap.sh. Granted AcrPush."
  type        = string
}

variable "monthly_budget" {
  description = "Monthly cost guardrail in the billing currency. Notifies at 50/80/100%."
  type        = number
  default     = 50
}

variable "budget_alert_email" {
  description = "Address the budget notifications go to."
  type        = string
}

variable "postgres_storage_mb" {
  description = "PostgreSQL storage. 32768 is the smallest, and P4 is its matching tier."
  type        = number
  default     = 32768
}

variable "log_daily_quota_gb" {
  description = "Hard daily ingestion cap on Log Analytics."
  type        = number
  default     = 0.5
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default = {
    project = "trackly"
    managed = "terraform"
  }
}
