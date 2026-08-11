variable "subscription_id" {
  type = string
}

variable "location" {
  type    = string
  default = "westeurope"
}

variable "image_tag" {
  description = "Only used when an app is first created; the deploy pipeline owns it afterwards."
  type        = string
  default     = "latest"
}

variable "client_secret" {
  description = "Plaintext OAuth2 client secret. Supplied as TF_VAR_client_secret."
  type        = string
  sensitive   = true
}

variable "client_secret_bcrypt" {
  description = "The same secret as a {bcrypt} hash. Supplied as TF_VAR_client_secret_bcrypt."
  type        = string
  sensitive   = true
}

variable "tfstate_resource_group" {
  type = string
}

variable "tfstate_storage_account" {
  type = string
}

variable "tfstate_container" {
  type    = string
  default = "tfstate"
}

variable "tags" {
  type = map(string)
  default = {
    project = "trackly"
    managed = "terraform"
  }
}
