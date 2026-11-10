export function invitationHash(tenantId: string, invitationId: string, token: string) {
  return "#" + new URLSearchParams({ tenantId, invitationId, invite: token });
}

export function readInvitation(hash: string) {
  const params = new URLSearchParams(hash.slice(1));
  const tenantId = params.get("tenantId")?.trim();
  const invitationId = params.get("invitationId")?.trim();
  const token = params.get("invite")?.trim();
  return tenantId && invitationId && token ? { tenantId, invitationId, token } : null;
}
