output "vm_public_ip" {
  description = "Public IP address of the deployed VM"
  value       = google_compute_address.app_ip.address
}

output "frontend_url" {
  description = "URL to access the frontend"
  value       = "http://${google_compute_address.app_ip.address}:3000"
}

output "backend_url" {
  description = "URL to access the backend API"
  value       = "http://${google_compute_address.app_ip.address}:8000"
}

output "audit_service_url" {
  description = "URL to access the audit service"
  value       = "http://${google_compute_address.app_ip.address}:8082"
}

output "ssh_command" {
  description = "Command to SSH into the VM"
  value       = "gcloud compute ssh contract-analyzer-vm --zone=${var.zone}"
}
