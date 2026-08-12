terraform {
  backend "azurerm" {
    key              = "staging.tfstate"
    use_azuread_auth = true
  }
}
