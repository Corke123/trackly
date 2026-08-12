terraform {
  backend "azurerm" {
    key              = "production.tfstate"
    use_azuread_auth = true
  }
}
