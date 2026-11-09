export type Role = "OWNER" | "ADMIN" | "MEMBER" | "READER";
export interface Tenant {
  id: string;
  name: string;
  enabled: boolean;
  role: Role;
}
export interface Member {
  userId: string;
  username?: string | null;
  role: Role;
}
export interface Invitation {
  id: string;
  userId: string;
  role: Role;
  expiresAt: string;
}
export interface CreatedInvitation {
  id: string;
  token: string;
}
export interface Client {
  clientId: string;
  ownerId: string;
  name: string;
  applicationId: string;
  kind: "application" | "machine" | "resource";
  approval: "PENDING" | "APPROVED" | "REJECTED";
  enabled: boolean;
  requestedScopes: string[];
  scopes: string[];
}
export interface Credentials {
  clientId: string;
  clientSecret: string | null;
}
export interface Permission {
  applicationId: string;
  subjectType: "USER" | "APPLICATION";
  subjectId: string;
  resourceType: string;
  resourceId: string;
  action: string;
}
export interface Audit {
  id: string;
  actorId: string;
  eventType: string;
  objectId: string;
  details: string;
  createdAt: string;
}
export type Catalog = Record<string, Record<string, string>>;
export interface Result<T> {
  code: number;
  message?: string;
  data: T;
}
export interface Session {
  token: string;
  username: string;
  userId: string;
}
