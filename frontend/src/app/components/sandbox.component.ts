import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-sandbox',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="sandbox-overlay" *ngIf="isOpen" (click)="close()">
      <div class="sandbox-panel glass-panel" (click)="$event.stopPropagation()">
        <header class="sandbox-header">
          <div class="header-main">
            <span class="icon">🚀</span>
            <div>
              <h3>Visionary Sandbox</h3>
              <p>Isolated Environment Execution</p>
            </div>
          </div>
          <button class="close-btn" (click)="close()">✕</button>
        </header>

        <div class="sandbox-content">
          <div class="code-column">
            <div class="label">SOURCE CODE</div>
            <pre><code>{{ code }}</code></pre>
          </div>
          
          <div class="output-column">
            <div class="label">EXECUTION OUTPUT</div>
            <div class="terminal">
              <div class="terminal-header">
                <span class="terminal-dot red"></span>
                <span class="terminal-dot yellow"></span>
                <span class="terminal-dot green"></span>
                <span class="terminal-title">atlasia-shell</span>
              </div>
              <div class="terminal-body">
                <div *ngIf="!output" class="waiting">Ready to execute...</div>
                <div *ngIf="output" class="output-text">{{ output }}</div>
                <div *ngIf="isRunning" class="cursor">_</div>
              </div>
            </div>
            
            <button class="accent-gradient run-btn" [disabled]="isRunning" (click)="execute()">
              {{ isRunning ? 'EXECUTING...' : 'RUN CODE' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
    styles: [`
    .sandbox-overlay {
      position: fixed;
      top: 0; left: 0; width: 100%; height: 100%;
      background: rgba(0,0,0,0.8);
      backdrop-filter: blur(10px);
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .sandbox-panel {
      width: 90%;
      max-width: 1200px;
      height: 80vh;
      display: flex;
      flex-direction: column;
      animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes slideUp { from { transform: translateY(50px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
    
    .sandbox-header {
      padding: 20px 30px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(255,255,255,0.05);
    }
    .header-main { display: flex; gap: 16px; align-items: center; }
    .header-main .icon { font-size: 2rem; }
    .header-main h3 { margin: 0; color: #38bdf8; letter-spacing: 0.05em; font-weight: 800; }
    .header-main p { margin: 2px 0 0; font-size: 0.8rem; color: #64748b; }
    
    .close-btn { background: transparent; border: none; color: #64748b; font-size: 1.5rem; cursor: pointer; transition: color 0.2s; }
    .close-btn:hover { color: white; }
    
    .sandbox-content {
      flex: 1;
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 30px;
      padding: 30px;
      min-height: 0;
    }
    
    .label { font-size: 0.7rem; font-weight: 800; color: #38bdf8; letter-spacing: 0.1em; margin-bottom: 12px; }
    
    .code-column { display: flex; flex-direction: column; min-height: 0; }
    pre {
      flex: 1;
      background: rgba(0,0,0,0.3);
      padding: 20px;
      border-radius: 12px;
      overflow: auto;
      border: 1px solid rgba(255,255,255,0.05);
      font-family: 'Fira Code', monospace;
      font-size: 0.9rem;
      line-height: 1.6;
    }
    
    .output-column { display: flex; flex-direction: column; gap: 20px; min-height: 0; }
    .terminal {
      flex: 1;
      background: #0f172a;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      border: 1px solid rgba(56, 189, 248, 0.2);
    }
    .terminal-header {
      padding: 10px 15px;
      background: rgba(255,255,255,0.03);
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .terminal-dot { width: 10px; height: 10px; border-radius: 50%; }
    .red { background: #ef4444; }
    .yellow { background: #f59e0b; }
    .green { background: #10b981; }
    .terminal-title { margin-left: 10px; font-size: 0.7rem; color: #64748b; font-family: monospace; }
    
    .terminal-body {
      padding: 20px;
      color: #10b981;
      font-family: 'Fira Code', monospace;
      font-size: 0.9rem;
      flex: 1;
      overflow-y: auto;
    }
    .waiting { color: #64748b; font-style: italic; }
    .output-text { white-space: pre-wrap; word-break: break-all; }
    .cursor { display: inline-block; animation: blink 1s infinite; }
    @keyframes blink { 50% { opacity: 0; } }
    
    .run-btn {
      padding: 18px;
      border: none;
      border-radius: 12px;
      color: white;
      font-weight: 800;
      letter-spacing: 0.1em;
      cursor: pointer;
      transition: all 0.3s;
    }
    .run-btn:disabled { opacity: 0.5; cursor: not-allowed; }
  `]
})
export class SandboxComponent {
    @Input() code: string = '';
    @Input() isOpen: boolean = false;
    @Output() onClose = new EventEmitter<void>();

    isRunning = false;
    output = '';

    close() {
        this.onClose.emit();
    }

    execute() {
        this.isRunning = true;
        this.output = 'Initializing execution environment...\nConnecting to container...\n';

        setTimeout(() => {
            this.output += '> Executing source code...\n';
            setTimeout(() => {
                // Simple simulation of output
                if (this.code.toLowerCase().includes('print')) {
                    const lines = this.code.split('\n');
                    const printLines = lines.filter(l => l.includes('print')).map(l => {
                        const match = l.match(/print\(['"](.+)['"]\)/);
                        return match ? match[1] : 'Output from print command';
                    });
                    this.output += '\n--- STDOUT ---\n' + printLines.join('\n');
                } else {
                    this.output += '\n--- STDOUT ---\nCode executed successfully. No output returned.\n';
                }
                this.output += '\nProcess finished with exit code 0';
                this.isRunning = false;
            }, 1500);
        }, 1000);
    }
}
