variable "SUBSCRIPTION_ID" {
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

variable "CLIENT_SECRET" {
  description = "Plaintext OAuth2 client secret. Supplied as TF_VAR_CLIENT_SECRET."
  type        = string
  sensitive   = true
}

variable "CLIENT_SECRET_BCRYPT" {
  description = "The same secret as a {bcrypt} hash. Supplied as TF_VAR_CLIENT_SECRET_BCRYPT."
  type        = string
  sensitive   = true
}

variable "TFSTATE_RESOURCE_GROUP" {
  type = string
}

variable "TFSTATE_STORAGE_ACCOUNT" {
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
