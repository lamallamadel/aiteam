import { Component, Input, OnChanges, SimpleChanges, ViewChild, ElementRef, AfterViewChecked, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrchestratorService } from '../services/orchestrator.service';
import { RunResponse, RunRequest } from '../models/orchestrator.model';
import { NeuralTraceComponent } from './neural-trace.component';
import { SandboxComponent } from './sandbox.component';

interface Message {
  role: 'user' | 'assistant';
  text: string;
  senderName?: string;
  orchestrationStep?: string;
  timestamp: string;
}

@Component({
  selector: 'app-chat-interface',
  standalone: true,
  imports: [CommonModule, FormsModule, NeuralTraceComponent, SandboxComponent],
  template: `
    <div class="chat-wrapper">
      <!-- Chat Header -->
      <header class="chat-header glass-panel" *ngIf="selectedRun || selectedPersona">
        <div class="header-info">
          <span class="header-icon" [ngClass]="selectedPersona ? 'gem' : 'run'">
            {{ selectedPersona ? '💎' : '🚀' }}
          </span>
          <div class="header-text">
            <h3>{{ selectedPersona ? (selectedPersona | uppercase) : (isDuelMode ? 'Gem Duel' : 'Orchestration Run') }}</h3>
            <p *ngIf="selectedRun">{{ selectedRun.repo }} #{{ selectedRun.issueNumber }}</p>
            <p *ngIf="selectedPersona">Interactive AI Gem Expertise</p>
            <p *ngIf="isDuelMode">Multi-AI Collaborative Session</p>
          </div>
        </div>
        <div class="header-actions">
            <span class="status-badge" [ngClass]="selectedPersona ? 'gem' : 'run'">
                {{ selectedPersona ? 'Direct Chat' : selectedRun?.status }}
            </span>
        </div>
      </header>

      <!-- Neural Trace Visualizer -->
      <app-neural-trace *ngIf="selectedRun" [steps]="assistantMessages"></app-neural-trace>

      <!-- New Run Form -->
      <div *ngIf="!selectedRun && !selectedPersona" class="new-run-form glass-panel">
        <h2>Start New Orchestration</h2>
        <div class="form-group">
          <label>Repository</label>
          <input type="text" [(ngModel)]="newRequest.repo" placeholder="owner/repo">
        </div>
        <div class="form-group">
          <label>Issue Number</label>
          <input type="number" [(ngModel)]="newRequest.issueNumber">
        </div>
        <div class="form-group">
          <label>Mode</label>
          <select [(ngModel)]="newRequest.mode">
            <option value="PLANNING">Planning</option>
            <option value="EXECUTION">Execution</option>
          </select>
        </div>
        <button class="accent-gradient start-btn" (click)="startRun()">Launch Orchestrator</button>
      </div>

      <!-- Messages List -->
      <div *ngIf="selectedRun || selectedPersona || isDuelMode" class="message-list" #scrollMe>
        <div *ngFor="let msg of messages" class="message" [ngClass]="[msg.role, msg.senderName ? 'persona-' + msg.senderName.toLowerCase() : '']">
          <div class="avatar">{{ msg.role === 'user' ? 'U' : (msg.senderName ? msg.senderName.charAt(0).toUpperCase() : 'AI') }}</div>
          <div class="bubble">
            <div class="sender-label" *ngIf="msg.senderName">{{ msg.senderName }}</div>
            <div class="message-text">{{ msg.text }}</div>
            
            <!-- Visionary Run Button -->
            <button *ngIf="msg.role === 'assistant' && hasCode(msg.text)" 
                    class="visionary-btn" (click)="openVisionary(msg.text)">
              ⚡ RUN CODE
            </button>

            <div *ngIf="msg.orchestrationStep" class="step-indicator">
              <span class="pulse"></span> Executing: {{ msg.orchestrationStep }}
            </div>
            
            <div class="message-footer">
                <span class="timestamp">{{ msg.timestamp | date:'shortTime' }}</span>
                <button *ngIf="msg.role === 'assistant'" class="copy-btn" (click)="copyToClipboard(msg.text)" title="Copy message">
                    📋
                </button>
            </div>
          </div>
        </div>
        
        <div *ngIf="isTyping" class="message assistant typing">
          <div class="avatar">...</div>
          <div class="bubble typing-bubble">
            <div class="typing-label">
              <span *ngFor="let p of Array.from(typingPersonas); let last = last">
                {{ p | uppercase }}{{ !last ? ', ' : '' }}
              </span>
              Thinking...
            </div>
            <div class="typing-indicator"><span></span><span></span><span></span></div>
          </div>
        </div>

        <div *ngIf="errorMessage" class="error-banner glass-panel">
          <span class="icon">⚠️</span>
          <div class="error-text">
            <strong>Execution Error</strong>
            <p>{{ errorMessage }}</p>
          </div>
          <button (click)="errorMessage = ''">✕</button>
        </div>
      </div>
      
      <!-- Input Area -->
      <div *ngIf="selectedRun || selectedPersona || isDuelMode" class="input-area">
        <button class="voice-btn" [class.recording]="isRecording" (click)="toggleRecord()" 
                [title]="isRecording ? 'Stop Recording' : 'Voice Command'">
          <span class="mic-icon">🎤</span>
          <div *ngIf="isRecording" class="pulse-ring"></div>
        </button>
        <input type="text" placeholder="Ask anything or provide feedback..." class="glass-panel" 
               [(ngModel)]="feedbackText" (keyup.enter)="sendFeedback()">
        <button class="accent-gradient" [disabled]="isTyping" (click)="sendFeedback()">
          {{ isTyping ? 'Thinking...' : 'Send' }}
        </button>
      </div>

      <!-- Visionary Sandbox -->
      <app-sandbox [code]="sandboxCode" [isOpen]="isSandboxOpen" (onClose)="isSandboxOpen = false"></app-sandbox>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; flex: 1; min-height: 0; width: 100%; overflow: hidden; }
    .chat-wrapper { display: flex; flex-direction: column; flex: 1; padding: 20px; background: rgba(0,0,0,0.1); border-radius: 12px; overflow: hidden; min-height: 0; width: 100%; }
    
    .chat-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; margin-bottom: 20px; border-bottom: 1px solid rgba(255,255,255,0.05); flex-shrink: 0; }
    .header-info { display: flex; align-items: center; gap: 16px; }
    .header-icon { width: 48px; height: 48px; border-radius: 12px; background: rgba(255,255,255,0.05); display: flex; align-items: center; justify-content: center; font-size: 1.5rem; }
    .header-icon.gem { background: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.2); }
    .header-text h3 { margin: 0; font-size: 1.1rem; color: #f8fafc; }
    .header-text p { margin: 2px 0 0; font-size: 0.85rem; color: #94a3b8; }
    .status-badge { padding: 4px 12px; border-radius: 20px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; background: rgba(255,255,255,0.05); color: #94a3b8; }
    .status-badge.gem { background: #38bdf8; color: white; }
    
    .new-run-form { max-width: 500px; margin: auto; padding: 40px; display: flex; flex-direction: column; gap: 20px; width: 100%; }
    .new-run-form h2 { margin: 0; color: #38bdf8; text-align: center; font-size: 1.5rem; }
    .form-group { display: flex; flex-direction: column; gap: 8px; }
    .form-group label { color: #94a3b8; font-size: 0.9rem; font-weight: 500; }
    .form-group input, .form-group select { padding: 12px; border: 1px solid rgba(255,255,255,0.1); background: rgba(0,0,0,0.2); color: white; border-radius: 8px; outline: none; transition: border-color 0.2s; }
    .form-group input:focus { border-color: #38bdf8; }
    .start-btn { padding: 16px; margin-top: 20px; font-size: 1.1rem; }
    
    app-neural-trace { flex-shrink: 0; min-height: 120px; }
    
    .message-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 20px; padding: 10px; min-height: 0; }
    .message { display: flex; gap: 12px; max-width: 85%; }
    .message.user { align-self: flex-end; flex-direction: row-reverse; }
    .avatar { width: 36px; height: 36px; border-radius: 50%; background: #1e293b; display: flex; align-items: center; justify-content: center; font-size: 0.8rem; font-weight: bold; color: #38bdf8; flex-shrink: 0; }
    .message.user .avatar { background: #38bdf8; color: white; }
    
    .bubble { padding: 12px 16px; border-radius: 12px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.05); position: relative; min-width: 0; }
    .message.user .bubble { background: #0ea5e9; color: white; border: none; border-bottom-right-radius: 2px; }
    .message.assistant .bubble { border-bottom-left-radius: 2px; }
    .sender-label { font-size: 0.7rem; font-weight: 800; color: #38bdf8; text-transform: uppercase; margin-bottom: 4px; letter-spacing: 0.05em; }
    
    /* Persona specific colors */
    .persona-aksil .bubble { border-left: 3px solid #f43f5e; }
    .persona-aksil .sender-label { color: #f43f5e; }
    .persona-morgan .bubble { border-left: 3px solid #8b5cf6; }
    .persona-morgan .sender-label { color: #8b5cf6; }
    .persona-atlasia .bubble { border-left: 3px solid #38bdf8; }
    .persona-atlasia .sender-label { color: #38bdf8; }
    .persona-sage .bubble { border-left: 3px solid #10b981; }
    .persona-sage .sender-label { color: #10b981; }
    .persona-pulse .bubble { border-left: 3px solid #f59e0b; }
    .persona-pulse .sender-label { color: #f59e0b; }
    
    .message-text { line-height: 1.5; white-space: pre-wrap; font-size: 0.95rem; word-break: break-word; }
    .message-text pre { background: rgba(0,0,0,0.3); padding: 12px; border-radius: 8px; overflow-x: auto; margin: 10px 0; border: 1px solid rgba(255,255,255,0.1); }
    .message-text code { font-family: 'Fira Code', monospace; font-size: 0.85rem; }
    .message-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; gap: 12px; }
    .timestamp { font-size: 0.7rem; color: #64748b; }
    .message.user .timestamp { color: rgba(255,255,255,0.7); }
    .copy-btn { background: transparent; border: none; cursor: pointer; font-size: 1rem; opacity: 0.4; transition: opacity 0.2s; padding: 0; display: flex; align-items: center; color: inherit; }
    .copy-btn:hover { opacity: 1; }
    
    .step-indicator { margin-top: 10px; padding: 8px; background: rgba(56, 189, 248, 0.1); border-radius: 6px; font-size: 0.8rem; color: #38bdf8; display: flex; align-items: center; gap: 8px; }
    .pulse { width: 8px; height: 8px; background: #38bdf8; border-radius: 50%; animation: pulse 1.5s infinite; }
    @keyframes pulse { 0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0.7); } 70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(56, 189, 248, 0); } 100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0); } }
    
    .typing-bubble { padding: 12px 20px; }
    .typing-label { font-size: 0.7rem; color: #94a3b8; margin-bottom: 4px; font-weight: 600; }
    .typing-indicator { display: flex; gap: 4px; }
    .typing-indicator span { width: 6px; height: 6px; background: #64748b; border-radius: 50%; animation: bounce 1.4s infinite ease-in-out both; }
    .typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
    .typing-indicator span:nth-child(2) { animation-delay: -0.16s; }
    @keyframes bounce { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1.0); } }

    .error-banner { 
      margin: 10px; padding: 12px 16px; background: rgba(239, 68, 68, 0.1); 
      border-left: 4px solid #ef4444; border-radius: 8px; display: flex; align-items: center; gap: 12px;
    }
    .error-banner .icon { font-size: 1.2rem; }
    .error-banner .error-text p { margin: 2px 0 0; font-size: 0.8rem; opacity: 0.8; }
    .error-banner button { margin-left: auto; background: transparent; border: none; color: white; cursor: pointer; }
    
    .input-area { margin-top: 20px; display: flex; gap: 12px; padding: 12px; background: rgba(0,0,0,0.2); border-radius: 12px; flex-shrink: 0; }
    .input-area input { flex: 1; padding: 12px; background: transparent; border: none; color: white; outline: none; font-size: 0.95rem; }
    .input-area button:disabled { opacity: 0.5; cursor: not-allowed; }

    .voice-btn {
      background: rgba(255,255,255,0.05);
      border: 1px solid rgba(255,255,255,0.1);
      width: 44px;
      height: 44px;
      border-radius: 50%;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      transition: all 0.3s;
    }
    .voice-btn:hover { background: rgba(56, 189, 248, 0.1); border-color: #38bdf8; }
    .voice-btn.recording { background: #ef4444; border-color: #ef4444; color: white; }
    .mic-icon { font-size: 1.2rem; z-index: 2; }
    
    .pulse-ring {
      position: absolute;
      width: 100%;
      height: 100%;
      border-radius: 50%;
      background: #ef4444;
      animation: mic-pulse 1.5s infinite;
      z-index: 1;
    }
    @keyframes mic-pulse {
      0% { transform: scale(1); opacity: 0.6; }
      100% { transform: scale(2.5); opacity: 0; }
    }

    .visionary-btn {
      margin-top: 10px;
      padding: 8px 16px;
      border: 1px solid rgba(56, 189, 248, 0.3);
      background: rgba(56, 189, 248, 0.1);
      color: #38bdf8;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 800;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s;
    }
    .visionary-btn:hover { background: rgba(56, 189, 248, 0.2); border-color: #38bdf8; }
  `]
})
export class ChatInterfaceComponent implements OnChanges, AfterViewChecked {
  Array = Array; // Allow using Array.from in template
  @Input() selectedRun?: RunResponse;
  @Input() selectedPersona?: string;

  @ViewChild('scrollMe') private myScrollContainer!: ElementRef;

  messages: Message[] = [];
  get assistantMessages() {
    return this.messages.filter(m => m.role === 'assistant');
  }
  newRequest: RunRequest = { repo: 'lamallamadel/orchistrateur', issueNumber: 1, mode: 'PLANNING' };
  feedbackText: string = '';
  isTyping = false;
  private shouldScroll = false;
  isDuelMode = false;
  typingPersonas = new Set<string>();
  private activePersonaCount = 0;
  errorMessage = '';
  isSandboxOpen = false;
  sandboxCode = '';

  isRecording = false;
  private recognition: any;

  constructor(
    private orchestratorService: OrchestratorService,
    private cdr: ChangeDetectorRef
  ) {
    this.initSpeechRecognition();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['selectedRun'] && this.selectedRun) {
      this.selectedPersona = undefined;
      this.isDuelMode = false;
      this.isTyping = false;
      this.typingPersonas.clear();
      this.activePersonaCount = 0;
      this.loadMessages();
    } else if (changes['selectedPersona'] && this.selectedPersona) {
      this.selectedRun = undefined;
      this.isDuelMode = false;
      this.isTyping = false;
      this.typingPersonas.clear();
      this.activePersonaCount = 0;
      this.initPersonaChat();
    } else if (!this.selectedRun && !this.selectedPersona) {
      this.messages = [];
      this.isDuelMode = false;
      this.isTyping = false;
      this.typingPersonas.clear();
      this.activePersonaCount = 0;
    }
    this.shouldScroll = true;
  }

  ngAfterViewChecked() {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  scrollToBottom(): void {
    try {
      if (this.myScrollContainer) {
        setTimeout(() => {
          this.myScrollContainer.nativeElement.scrollTop = this.myScrollContainer.nativeElement.scrollHeight;
        }, 50);
      }
    } catch (err) { }
  }

  initPersonaChat() {
    this.messages = [{
      role: 'assistant',
      text: `Hello! I am ${this.selectedPersona?.toUpperCase()}, your specialized AI Gem. How can I help you today?`,
      timestamp: new Date().toISOString()
    }];
    this.speak(this.messages[0].text);
  }

  loadMessages() {
    if (!this.selectedRun) return;

    this.orchestratorService.getArtifacts(this.selectedRun.id).subscribe({
      next: (artifacts: any[]) => {
        this.messages = artifacts
          .filter((a: any) => a.artifactType === 'log' || a.artifactType === 'step')
          .map((a: any) => ({
            role: 'assistant',
            text: a.payload,
            orchestrationStep: a.agentName,
            timestamp: a.createdAt
          }));

        this.messages.unshift({
          role: 'user',
          text: `Starting orchestration for ${this.selectedRun?.repo} #${this.selectedRun?.issueNumber}`,
          timestamp: this.selectedRun!.createdAt
        });
        this.shouldScroll = true;
        this.cdr.detectChanges();
      },
      error: (err) => this.handleError(err)
    });
  }

  startRun() {
    this.orchestratorService.createRun(this.newRequest).subscribe({
      next: (run: RunResponse) => {
        this.selectedRun = run;
        this.loadMessages();
      },
      error: (err) => this.handleError(err)
    });
  }

  sendFeedback() {
    if (!this.feedbackText) return;

    const userMsg: Message = {
      role: 'user',
      text: this.feedbackText,
      timestamp: new Date().toISOString()
    };
    this.messages.push(userMsg);
    const textToChat = this.feedbackText;
    const mentions = this.feedbackText.match(/@(\w+)/g);
    const mentionedPersonas = mentions ? mentions.map(m => m.substring(1).toLowerCase()) : [];

    this.feedbackText = '';
    this.shouldScroll = true;

    if (mentionedPersonas.length > 0) {
      this.isDuelMode = true;
      this.activePersonaCount = mentionedPersonas.length;
      this.isTyping = true;
      this.errorMessage = '';

      mentionedPersonas.forEach(personaName => {
        this.typingPersonas.add(personaName);
        this.orchestratorService.chat(personaName, textToChat).subscribe({
          next: (res) => {
            this.messages.push({
              role: 'assistant',
              text: res.response,
              senderName: personaName.charAt(0).toUpperCase() + personaName.slice(1),
              timestamp: new Date().toISOString()
            });

            this.typingPersonas.delete(personaName);
            this.activePersonaCount--;
            if (this.activePersonaCount <= 0) {
              this.isTyping = false;
            }
            this.shouldScroll = true;
            this.cdr.detectChanges();
          },
          error: (err) => {
            this.typingPersonas.delete(personaName);
            this.activePersonaCount--;
            this.handleError(err);
          }
        });
      });
    } else if (this.selectedPersona) {
      this.isTyping = true;
      this.typingPersonas.add(this.selectedPersona);
      this.errorMessage = '';

      this.orchestratorService.chat(this.selectedPersona, textToChat).subscribe({
        next: (res) => {
          this.isTyping = false;
          this.typingPersonas.clear();
          this.messages.push({
            role: 'assistant',
            text: res.response,
            senderName: this.selectedPersona,
            timestamp: new Date().toISOString()
          });
          this.speak(res.response);
          this.shouldScroll = true;
          this.cdr.detectChanges();
        },
        error: (err) => this.handleError(err)
      });
    }
  }

  private handleError(err: any) {
    this.isTyping = false;
    this.typingPersonas.clear();
    if (err.status === 401) {
      this.errorMessage = 'Session expired. Please log in again.';
    } else {
      this.errorMessage = 'Communication failed with Atlasia Neural Link.';
    }
    this.cdr.detectChanges();
  }

  private initSpeechRecognition() {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SpeechRecognition) {
      this.recognition = new SpeechRecognition();
      this.recognition.continuous = false;
      this.recognition.lang = 'en-US';

      this.recognition.onresult = (event: any) => {
        const transcript = event.results[0][0].transcript;
        this.feedbackText = transcript;
        this.cdr.detectChanges();
        setTimeout(() => this.sendFeedback(), 500);
      };

      this.recognition.onend = () => {
        this.isRecording = false;
        this.cdr.detectChanges();
      };

      this.recognition.onerror = () => {
        this.isRecording = false;
        this.cdr.detectChanges();
      };
    }
  }

  toggleRecord() {
    if (!this.recognition) {
      alert('Speech recognition is not supported in your browser.');
      return;
    }

    if (this.isRecording) {
      this.recognition.stop();
    } else {
      this.isRecording = true;
      this.recognition.start();
    }
  }

  speak(text: string) {
    if (!window.speechSynthesis) return;

    // Clean text for speech (remove markdown code blocks)
    const cleanText = text.replace(/```[\s\S]*?```/g, '').replace(/`[^`]*`/g, '');

    const utterance = new SpeechSynthesisUtterance(cleanText);
    utterance.rate = 1;
    utterance.pitch = 1;

    // Try to find a nice voice
    const voices = window.speechSynthesis.getVoices();
    if (this.selectedPersona?.toLowerCase() === 'morgan') {
      utterance.pitch = 0.8; // Deeper voice
    } else if (this.selectedPersona?.toLowerCase() === 'aksil') {
      utterance.rate = 1.2; // Faster voice
    }

    window.speechSynthesis.speak(utterance);
  }

  async copyToClipboard(text: string) {
    try {
      await navigator.clipboard.writeText(text);
    } catch (err) {
      console.error('Failed to copy: ', err);
    }
  }

  hasCode(text: string): boolean {
    return text.includes('```');
  }

  openVisionary(text: string) {
    const match = text.match(/```(\w+)?\n?([\s\S]+?)```/);
    if (match) {
      this.sandboxCode = match[2].trim();
      this.isSandboxOpen = true;
    }
  }
}
