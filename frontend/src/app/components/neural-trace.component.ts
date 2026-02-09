import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

interface Node {
    id: string;
    label: string;
    type: 'agent' | 'step';
    x: number;
    y: number;
    active: boolean;
}

interface Connection {
    from: string;
    to: string;
}

@Component({
    selector: 'app-neural-trace',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="neural-container glass-panel">
      <div class="background-grid"></div>
      <svg class="graph-svg" viewBox="0 0 800 200">
        <!-- Connections -->
        <line *ngFor="let conn of connections" 
              [attr.x1]="getNode(conn.from).x" [attr.y1]="getNode(conn.from).y"
              [attr.x2]="getNode(conn.to).x" [attr.y2]="getNode(conn.to).y"
              class="connection-line" />

        <!-- Nodes -->
        <g *ngFor="let node of nodes" [class.active]="node.active">
          <circle [attr.cx]="node.x" [attr.cy]="node.y" [attr.r]="node.type === 'agent' ? 12 : 8" 
                  [class]="'node ' + node.type" />
          <text [attr.x]="node.x" [attr.y]="node.y + 25" class="node-label">{{ node.label }}</text>
          
          <!-- Pulse animation for active node -->
          <circle *ngIf="node.active" [attr.cx]="node.x" [attr.cy]="node.y" [attr.r]="15" class="node-pulse" />
        </g>
      </svg>
      
      <div class="trace-overlay">
        <span class="status"><span class="pulse-dot"></span> NEURAL TRACE ACTIVE</span>
        <span class="step-count">{{ nodes.length }} NODES SYNCED</span>
      </div>
    </div>
  `,
    styles: [`
    .neural-container {
      height: 200px;
      margin-bottom: 20px;
      position: relative;
      overflow: hidden;
      background: rgba(15, 23, 42, 0.4);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .background-grid {
      position: absolute;
      top: 0; left: 0; right: 0; bottom: 0;
      background-image: linear-gradient(rgba(56, 189, 248, 0.05) 1px, transparent 1px),
                        linear-gradient(90deg, rgba(56, 189, 248, 0.05) 1px, transparent 1px);
      background-size: 20px 20px;
      pointer-events: none;
    }
    .graph-svg { width: 100%; height: 100%; }
    
    .connection-line {
      stroke: rgba(56, 189, 248, 0.2);
      stroke-width: 1.5;
      stroke-dasharray: 4;
      animation: dash 20s linear infinite;
    }
    @keyframes dash { to { stroke-dashoffset: -1000; } }
    
    .node {
      fill: #1e293b;
      stroke: rgba(56, 189, 248, 0.5);
      stroke-width: 2;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }
    .node.agent { stroke: #38bdf8; }
    .node.step { stroke: #818cf8; }
    
    .active .node {
      fill: #38bdf8;
      stroke: #fff;
      filter: drop-shadow(0 0 8px #38bdf8);
    }
    
    .node-label {
      fill: #94a3b8;
      font-size: 10px;
      text-anchor: middle;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .active .node-label { fill: #fff; text-shadow: 0 0 5px #38bdf8; }
    
    .node-pulse {
      fill: none;
      stroke: #38bdf8;
      stroke-width: 1;
      animation: ripple 1.5s infinite;
    }
    @keyframes ripple {
      0% { transform: scale(1); opacity: 0.8; }
      100% { transform: scale(2); opacity: 0; }
    }
    
    .trace-overlay {
      position: absolute;
      top: 12px;
      left: 12px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      pointer-events: none;
    }
    .status {
      font-size: 0.65rem;
      font-weight: 800;
      color: #38bdf8;
      letter-spacing: 0.1em;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .pulse-dot {
      width: 6px;
      height: 6px;
      background: #38bdf8;
      border-radius: 50%;
      animation: blink 1s infinite;
    }
    @keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
    .step-count { font-size: 0.6rem; color: #64748b; font-weight: 600; }
  `]
})
export class NeuralTraceComponent implements OnChanges {
    @Input() steps: any[] = [];

    nodes: Node[] = [];
    connections: Connection[] = [];

    ngOnChanges(changes: SimpleChanges) {
        if (changes['steps']) {
            this.buildGraph();
        }
    }

    buildGraph() {
        const newNodes: Node[] = [];
        const newConnections: Connection[] = [];

        // Group steps by agent
        const agents = Array.from(new Set(this.steps.map(s => s.orchestrationStep || s.agentName).filter(Boolean)));

        const agentSpacing = 800 / (agents.length + 1);

        agents.forEach((agentName: any, idx) => {
            const agentId = `agent-${agentName}`;
            newNodes.push({
                id: agentId,
                label: agentName,
                type: 'agent',
                x: agentSpacing * (idx + 1),
                y: 60,
                active: false
            });

            const agentSteps = this.steps.filter(s => (s.orchestrationStep || s.agentName) === agentName);
            const stepSpacing = 40;

            agentSteps.forEach((step, sIdx) => {
                const stepId = `step-${step.timestamp || sIdx}`;
                newNodes.push({
                    id: stepId,
                    label: `Step ${sIdx + 1}`,
                    type: 'step',
                    x: agentSpacing * (idx + 1) + (sIdx % 2 === 0 ? 30 : -30),
                    y: 110 + (sIdx * stepSpacing),
                    active: sIdx === agentSteps.length - 1
                });

                newConnections.push({ from: agentId, to: stepId });
                if (sIdx > 0) {
                    const prevStepId = `step-${agentSteps[sIdx - 1].timestamp || (sIdx - 1)}`;
                    newConnections.push({ from: prevStepId, to: stepId });
                }
            });

            if (idx === agents.length - 1) {
                newNodes.find(n => n.id === agentId)!.active = true;
            }
        });

        this.nodes = newNodes;
        this.connections = newConnections;
    }

    getNode(id: string): Node {
        return this.nodes.find(n => n.id === id) || { id: '', label: '', type: 'step', x: 0, y: 0, active: false };
    }
}
