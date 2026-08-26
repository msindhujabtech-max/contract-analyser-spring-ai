variable "project_id" {
  description = "Your GCP Project ID (find it at https://console.cloud.google.com)"
  type        = string
}

variable "region" {
  description = "GCP region to deploy in"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "GCP zone to deploy in"
  type        = string
  default     = "us-central1-a"
}

variable "machine_type" {
  description = "VM machine type (e2-standard-4 recommended for Ollama LLM)"
  type        = string
  default     = "e2-standard-4"
}

variable "disk_size_gb" {
  description = "Boot disk size in GB"
  type        = number
  default     = 50
}
