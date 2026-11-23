variable "aws_region" {
  description = "AWS region for infrastructure provisioning"
  type        = string
  default     = "us-east-1"
}

variable "aws_endpoint_url" {
  description = "Target AWS endpoint URL (LocalStack emulator or live AWS)"
  type        = string
  default     = "http://localhost:4566"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
  default     = "dev"
}
