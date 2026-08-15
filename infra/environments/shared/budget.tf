resource "azurerm_consumption_budget_subscription" "trackly" {
  name            = "budget-trackly"
  subscription_id = "/subscriptions/${var.SUBSCRIPTION_ID}"

  amount     = var.monthly_budget
  time_grain = "Monthly"

  time_period {
    start_date = formatdate("YYYY-MM-01'T'00:00:00Z", timestamp())
  }

  notification {
    enabled        = true
    threshold      = 50
    operator       = "GreaterThan"
    threshold_type = "Actual"
    contact_emails = [var.BUDGET_ALERT_EMAIL]
  }

  notification {
    enabled        = true
    threshold      = 80
    operator       = "GreaterThan"
    threshold_type = "Actual"
    contact_emails = [var.BUDGET_ALERT_EMAIL]
  }

  notification {
    enabled        = true
    threshold      = 100
    operator       = "GreaterThan"
    threshold_type = "Forecasted"
    contact_emails = [var.BUDGET_ALERT_EMAIL]
  }

  lifecycle {
    ignore_changes = [time_period]
  }
}
