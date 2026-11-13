export interface User {
  id: string;
  login: string;
  name: string;
  roles: string[];
  active: boolean;
  mustChangePassword: boolean;
}
export const session = $state<{ user: User | null }>({ user: null });
export const isAdmin = () => !!session.user?.roles.includes("ADMIN");
export const canEdit = () =>
  !!session.user?.roles.some((r) => r === "ADMIN" || r === "EDITOR");
