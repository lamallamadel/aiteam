import { Component, Input, Output, EventEmitter, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-sandbox',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="sandbox-overlay" *ngIf="isOpen" (click)="close()">
      <div class="sandbox-slide-panel" (click)="$event.stopPropagation()">
        <header class="sandbox-header">
          <div class="header-main">
            <span class="icon">⚡</span>
            <div>
              <h3>Visionary Sandbox</h3>
              <p>Isolated Environment Execution</p>
            </div>
          </div>
          <button class="close-btn" (click)="close()">✕</button>
        </header>

        <div class="sandbox-content">
          <div class="section-container">
            <div class="label">SOURCE CODE</div>
            <div class="code-editor">
              <pre><code>{{ code }}</code></pre>
            </div>
          </div>
          
          <div class="section-container">
            <div class="label">ATLASIA TERMINAL</div>
            <div class="terminal">
              <div class="terminal-header">
                <div class="controls">
                  <span class="dot red"></span>
                  <span class="dot yellow"></span>
                  <span class="dot green"></span>
                </div>
                <span class="title">atlasia-shell v1.0.4</span>
              </div>
              <div class="terminal-body" #terminalBody>
                <div *ngIf="!outputLines.length" class="waiting">Ready to execute session...</div>
                <div *ngFor="let line of outputLines" class="output-line" [ngClass]="line.type">
                  <span class="prompt" *ngIf="line.type === 'command'">$</span>
                  {{ line.text }}
                </div>
                <div *ngIf="isRunning" class="cursor-line"><span class="cursor">_</span></div>
              </div>
            </div>
            
            <button class="run-btn" [disabled]="isRunning" (click)="execute()">
              {{ isRunning ? 'EXECUTING...' : 'RUN CODE' }}
            </button>
          </div>

          <div class="section-container preview-section" *ngIf="hasHTML()">
            <div class="label">LIVE PREVIEW</div>
            <div class="preview-frame">
              <iframe #preview [srcdoc]="code" frameborder="0"></iframe>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .sandbox-overlay {
      position: fixed;
      top: 0; left: 0; width: 100%; height: 100%;
      background: rgba(0,0,0,0.6);
      backdrop-filter: blur(8px);
      z-index: 1000;
      display: flex;
      justify-content: flex-end;
      opacity: 0;
      animation: fadeIn 0.3s forwards;
    }
    @keyframes fadeIn { to { opacity: 1; } }

    .sandbox-slide-panel {
      width: 500px;
      height: 100%;
      display: flex;
      flex-direction: column;
      transform: translateX(100%);
      animation: slideIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
      border-left: 1px solid var(--border);
      border-radius: 0;
      background: var(--surface-elevated);
    }
    @keyframes slideIn { to { transform: translateX(0); } }
    
    .sandbox-header {
      padding: 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--border);
      background: rgba(15, 23, 42, 0.4);
    }
    .header-main { display: flex; gap: 16px; align-items: center; }
    .header-main .icon { font-size: 1.8rem; text-shadow: 0 0 10px #38bdf8; }
    .header-main h3 { margin: 0; color: #38bdf8; letter-spacing: 0.05em; font-weight: 800; font-size: 1.1rem; }
    .header-main p { margin: 2px 0 0; font-size: 0.75rem; color: #64748b; font-weight: 600; text-transform: uppercase; }
    
    .close-btn { background: transparent; border: none; color: #64748b; font-size: 1.2rem; cursor: pointer; transition: all 0.2s; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 50%; }
    .close-btn:hover { color: white; background: rgba(255,255,255,0.05); }
    
    .sandbox-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 24px;
      padding: 24px;
      overflow-y: auto;
      min-height: 0;
    }
    
    .section-container { display: flex; flex-direction: column; min-height: 0; flex-shrink: 0; }
    .preview-section { flex: 1; min-height: 250px; }
    .label { font-size: 0.65rem; font-weight: 900; color: #38bdf8; letter-spacing: 0.15em; margin-bottom: 12px; opacity: 0.8; }
    
    .code-editor {
      background: rgba(0,0,0,0.4);
      padding: 16px;
      border-radius: 12px;
      border: 1px solid var(--border);
      max-height: 200px;
      overflow: auto;
    }
    pre { margin: 0; font-family: 'Fira Code', monospace; font-size: 0.85rem; line-height: 1.5; color: #e2e8f0; }
    
    .terminal {
      background: #020617;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      border: 1px solid var(--border);
      height: 250px;
    }
    .terminal-header {
      padding: 10px 16px;
      background: rgba(255,255,255,0.03);
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid var(--border);
    }
    .controls { display: flex; gap: 6px; }
    .dot { width: 8px; height: 8px; border-radius: 50%; }
    .red { background: #ef4444; }
    .yellow { background: #f59e0b; }
    .green { background: #10b981; }
    .title { font-size: 0.6rem; color: #475569; font-family: 'Fira Code', monospace; font-weight: 600; }
    
    .terminal-body {
      padding: 16px;
      color: #10b981;
      font-family: 'Fira Code', monospace;
      font-size: 0.85rem;
      flex: 1;
      overflow-y: auto;
      scrollbar-width: thin;
      scrollbar-color: rgba(56, 189, 248, 0.2) transparent;
    }
    .waiting { color: #475569; font-style: italic; }
    .output-line { margin-bottom: 4px; white-space: pre-wrap; word-break: break-all; }
    .command { color: #f8fafc; font-weight: 600; }
    .system { color: #38bdf8; font-style: italic; }
    .error { color: #ef4444; }
    .prompt { color: #38bdf8; margin-right: 8px; }
    .cursor-line { display: flex; align-items: center; height: 1.2rem; }
    .cursor { display: inline-block; width: 8px; height: 16px; background: #38bdf8; animation: blink 1s infinite; }
    @keyframes blink { 50% { opacity: 0; } }
    
    .run-btn {
      margin-top: 16px;
      padding: 14px;
      border: none;
      border-radius: 12px;
      background: var(--accent-active);
      color: white;
      font-weight: 800;
      letter-spacing: 0.1em;
      cursor: pointer;
      font-size: 0.85rem;
      transition: all 0.3s;
    }
    .run-btn:hover:not(:disabled) { transform: translateY(-2px); opacity: 0.9; }
    .run-btn:disabled { opacity: 0.5; cursor: not-allowed; }

    .preview-frame {
      flex: 1;
      background: white;
      border-radius: 8px;
      overflow: hidden;
      margin-top: 4px;
      border: 1px solid var(--border);
    }
    iframe { width: 100%; height: 100%; }
  `]
})
export class SandboxComponent implements AfterViewChecked {
  @Input() code: string = '';
  @Input() isOpen: boolean = false;
  @Output() onClose = new EventEmitter<void>();

  @ViewChild('terminalBody') private terminalBody!: ElementRef;
  private shouldScrollTerminal = false;

  isRunning = false;
  outputLines: { text: string, type: 'command' | 'stdout' | 'system' | 'error' }[] = [];

  ngAfterViewChecked() {
    if (this.shouldScrollTerminal) {
      this.scrollToBottom();
      this.shouldScrollTerminal = false;
    }
  }

  private scrollToBottom() {
    try {
      this.terminalBody.nativeElement.scrollTop = this.terminalBody.nativeElement.scrollHeight;
    } catch (err) { }
  }

  close() {
    this.onClose.emit();
  }

  hasHTML() {
    const lowerCode = this.code.toLowerCase();
    return lowerCode.includes('<!doctype') || lowerCode.includes('<html') || lowerCode.includes('<div');
  }

  execute() {
    this.isRunning = true;
    this.outputLines = [];

    this.addOutput('Initializing Visionary virtualization layer...', 'system');

    setTimeout(() => {
      this.addOutput(`Loading execution context for: ${this.detectLanguage()}`, 'system');

      setTimeout(() => {
        this.addOutput(`${this.code.split('\n')[0].substring(0, 40)}...`, 'command');

        setTimeout(() => {
          this.runSimulation();
        }, 800);
      }, 600);
    }, 800);
  }

  private addOutput(text: string, type: 'command' | 'stdout' | 'system' | 'error' = 'stdout') {
    this.outputLines.push({ text, type });
    this.shouldScrollTerminal = true;
  }

  private detectLanguage() {
    if (this.hasHTML()) return 'Web/HTML';
    if (this.code.includes('import ') || this.code.includes('print(')) return 'Python Script';
    if (this.code.includes('const ') || this.code.includes('function ')) return 'Node.js/JavaScript';
    return 'System Shell';
  }

  private runSimulation() {
    if (this.code.toLowerCase().includes('print')) {
      const lines = this.code.split('\n');
      const printLines = lines.filter(l => l.includes('print')).map(l => {
        const match = l.match(/print\(['"](.+)['"]\)/);
        return match ? match[1] : 'Executing print...';
      });

      printLines.forEach((line, i) => {
        setTimeout(() => {
          this.addOutput(line, 'stdout');
          if (i === printLines.length - 1) this.finish();
        }, (i + 1) * 300);
      });
    } else if (this.hasHTML()) {
      this.addOutput('Web interface rendered in Live Preview.', 'system');
      this.finish();
    } else {
      this.addOutput('Process executing...', 'system');
      setTimeout(() => {
        this.addOutput('Command completed successfully.', 'stdout');
        this.finish();
      }, 1000);
    }
  }

  private finish() {
    this.addOutput('Session terminated (exit code 0)', 'system');
    this.isRunning = false;
  }
}
