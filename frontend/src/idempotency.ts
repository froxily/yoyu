export class IdempotencyKeys {
  private body?: string
  private key = ''

  keyFor(body: string): string {
    if (body !== this.body) {
      this.body = body
      this.key = crypto.randomUUID()
    }
    return this.key
  }

  reset() {
    this.body = undefined
  }
}
