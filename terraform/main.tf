terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
  required_version = ">= 1.5.0"
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# --- Static IP Address ---
resource "google_compute_address" "app_ip" {
  name   = "contract-app-ip"
  region = var.region
}

# --- Firewall Rule: Allow app ports ---
resource "google_compute_firewall" "allow_app_ports" {
  name    = "allow-contract-app-traffic"
  network = "default"

  allow {
    protocol = "tcp"
    ports    = ["3000", "8000", "8082", "22"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["contract-app"]
}

# --- VM Instance ---
resource "google_compute_instance" "contract_vm" {
  name         = "contract-analyzer-vm"
  machine_type = var.machine_type
  tags         = ["contract-app", "http-server"]

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
      size  = var.disk_size_gb
    }
  }

  network_interface {
    network = "default"
    access_config {
      nat_ip = google_compute_address.app_ip.address
    }
  }

  metadata_startup_script = <<-EOF
    #!/bin/bash
    set -e

    # Install Docker
    apt-get update
    apt-get install -y ca-certificates curl gnupg git
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Enable Docker for all users
    systemctl enable docker
    systemctl start docker

    # Create shared Docker network
    docker network create contract-network || true

    # Clone and start audit service (must start first)
    cd /opt
    git clone https://github.com/msindhujabtech-max/contract-audit-service.git
    cd /opt/contract-audit-service
    docker compose up -d --build

    # Clone and start analyser service
    cd /opt
    git clone https://github.com/msindhujabtech-max/contract-analyser-spring-ai.git
    cd /opt/contract-analyser-spring-ai
    docker compose up -d --build
  EOF
}
