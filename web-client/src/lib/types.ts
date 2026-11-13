export interface Work {
  id: string;
  code: string;
  title: string;
  author: string;
  description: string;
  language: string;
  originType: string;
  collectionId: string;
  status: string;
  targetMinutes: number;
  featured: boolean;
  rolesCount: number;
  complexity: string;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
  tags: string[];
  script: string;
}
export interface Collection {
  id: string;
  name: string;
  active: boolean;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
export interface Content {
  id: string;
  segments: {
    id: string;
    name: string;
    orderInTextWork: number;
    fragments: {
      id: string;
      orderInSegment: number;
      content: string;
      voicePartId: string;
    }[];
  }[];
  voiceParts: { id: string; name: string; totalFragmentsCount: number }[];
}
export interface Draft {
  id: string;
  originalFileName: string;
  sizeBytes: number;
  createdAt: string;
  fragmentId: string;
}
export interface Render {
  id: string;
  textWorkId: string;
  status: string;
  outputMode: string;
  createdAt: string;
  error?: string;
  outputs: { segmentId: string | null; contentType: string }[];
}
