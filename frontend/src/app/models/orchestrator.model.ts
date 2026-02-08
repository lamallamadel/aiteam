export interface RunRequest {
    repo: string;
    issueNumber: number;
    mode: string;
}

export interface RunResponse {
    id: string;
    repo: string;
    issueNumber: number;
    status: string;
    createdAt: string;
    updatedAt: string;
    currentAgent?: string;
    ciFixCount: number;
    e2eFixCount: number;
    artifacts?: ArtifactSummary[];
}

export interface ArtifactSummary {
    id: string;
    agentName: string;
    artifactType: string;
    createdAt: string;
}

export interface ArtifactResponse {
    id: string;
    agentName: string;
    artifactType: string;
    payload: string;
    createdAt: string;
}
