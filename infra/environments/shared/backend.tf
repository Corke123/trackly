terraform {
  backend "azurerm" {
    key              = "shared.tfstate"
    use_azuread_auth = true
  }
}
