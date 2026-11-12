import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Subscription } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';

import { UiCardComponent } from '../../shared/ui/card/ui-card/ui-card.component';
import { UiButtonComponent } from '../../shared/ui/button/ui-button/ui-button.component';

import { AuthService } from '../../core/auth.service';
import { MessagesService } from '../../service/message.service';
import { ProjectRole } from '../../service/project-members.service';

import { ConversationDto } from '../../model/conversation.model';
import { ProjectMessageDto } from '../../model/project-message.model';
import { interval, filter, switchMap } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-messages',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    LucideAngularModule,
    UiCardComponent,
    UiButtonComponent,
  ],
  templateUrl: './messages.component.html',
})
export class MessagesComponent implements OnInit, OnDestroy {
  // Left panel
  conversations: ConversationDto[] = [];
  filteredConversations: ConversationDto[] = [];
  projectSearch = '';

  // Selected
  selected: ConversationDto | null = null;

  // Messages
  messages: ProjectMessageDto[] = [];
  loadingConvs = false;
  loadingMessages = false;
  loadingOlder = false;
  hasMore = true;
  trackByMsgId = (_: number, m: { id: string }) => m.id;

  // UX state
  errorConvs: string | null = null;
  errorMessages: string | null = null;
  sending = false;

  // Optional UX (placeholders)
  typingText: string | null = null; // ex: "Quelqu’un écrit…"
  // read receipts placeholder: could be added per message later

  // Form
  form: FormGroup<{ content: FormControl<string> }>;

  private sub = new Subscription();
  private liveSub?: Subscription;

  @ViewChild('messagesViewport', { static: false })
  messagesViewport?: ElementRef<HTMLDivElement>;

  constructor(
    private api: MessagesService,
    private fb: FormBuilder,
    private auth: AuthService
  ) {
    this.form = this.fb.group({
      content: this.fb.nonNullable.control('', [
        Validators.required,
        Validators.minLength(1),
      ]),
    });
  }
  private startLiveRefresh() {
    this.liveSub?.unsubscribe();

    this.liveSub = interval(10000)
      .pipe(
        filter(() => !!this.selected && !this.sending),
        switchMap(() =>
          this.api.getProjectMessages(this.selected!.projectId, 20)
        )
      )
      .subscribe({
        next: (latest) => this.mergeLatest(latest ?? []),
        error: () => {}, // silencieux: on ne spam pas l'UI
      });
  }

  private stopLiveRefresh() {
    this.liveSub?.unsubscribe();
    this.liveSub = undefined;
  }
  private mergeLatest(latest: ProjectMessageDto[]) {
    if (!this.selected) return;

    const viewport = this.messagesViewport?.nativeElement;
    const nearBottom = viewport
      ? viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight < 120
      : true;

    const existing = new Set(this.messages.map((m) => m.id));
    const normalized = latest
      .slice()
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      .filter((m) => !existing.has(m.id));

    if (!normalized.length) return;

    this.messages = [...this.messages, ...normalized].sort((a, b) =>
      a.createdAt.localeCompare(b.createdAt)
    );

    if (nearBottom) queueMicrotask(() => this.scrollToBottom());
  }

  ngOnInit(): void {
    this.loadConversations();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.stopLiveRefresh();
  }

  // ===== Helpers
  get myUserId(): string {
    return String(this.auth.getCurrentUserId?.() ?? '');
  }

  get contentCtrl(): FormControl<string> {
    return this.form.controls.content;
  }

  roleLabel(role: ProjectRole): string {
    if (role === 0) return 'Owner';
    if (role === 1) return 'Member';
    return 'Observer';
  }

  rolePillClass(role: ProjectRole): string {
    if (role === 0)
      return 'bg-purple-500/15 text-purple-700 border border-purple-500/25 dark:text-purple-100';
    if (role === 1)
      return 'bg-indigo-500/15 text-indigo-700 border border-indigo-500/25 dark:text-indigo-100';
    return 'bg-fuchsia-500/15 text-fuchsia-700 border border-fuchsia-500/25 dark:text-fuchsia-100';
  }

  isMine(m: ProjectMessageDto): boolean {
    return String(m.userId) === this.myUserId;
  }

  displaySenderName(m: ProjectMessageDto): string {
    if (this.isMine(m)) return 'Vous';

    const full = (m.userFullName ?? '').trim();
    if (full) return full;

    const email = (m.userEmail ?? '').trim();
    if (email) return email;

    return 'Utilisateur';
  }

  initials(value: string | null | undefined): string {
    const s = (value ?? '').trim();
    if (!s) return 'U';

    // si c'est un email -> prendre avant @
    const base = s.includes('@') ? s.split('@')[0] : s;

    const parts = base
      .split(/[.\s_-]+/)
      .filter(Boolean)
      .slice(0, 2);
    const ini = parts.map((p) => (p[0] ?? '').toUpperCase()).join('');
    return ini || 'U';
  }

  // ===== Conversations
  loadConversations(): void {
    this.loadingConvs = true;
    this.errorConvs = null;

    const s = this.api.getConversations().subscribe({
      next: (items) => {
        this.conversations = items ?? [];
        this.applyConversationFilter();
        this.loadingConvs = false;

        if (!this.selected && this.filteredConversations.length) {
          this.selectConversation(this.filteredConversations[0]);
        }
      },
      error: (err) => {
        console.error(err);
        this.loadingConvs = false;
        this.errorConvs = 'Erreur lors du chargement des conversations.';
      },
    });

    this.sub.add(s);
  }

  onSearchProject(value: string): void {
    this.projectSearch = value ?? '';
    this.applyConversationFilter();
  }

  applyConversationFilter(): void {
    const term = this.projectSearch.trim().toLowerCase();
    if (!term) {
      this.filteredConversations = [...this.conversations];
      return;
    }

    this.filteredConversations = this.conversations.filter((c) => {
      const name = (c.projectName ?? '').toLowerCase();
      const preview = (c.lastMessagePreview ?? '').toLowerCase();
      return name.includes(term) || preview.includes(term);
    });
  }

  selectConversation(c: ConversationDto): void {
    this.selected = c;
    this.messages = [];
    this.hasMore = true;
    this.errorMessages = null;
    this.loadMessagesInitial();
  }

  private loadMessagesInitial(): void {
    if (!this.selected) return;
    this.loadingMessages = true;
    this.errorMessages = null;

    const s = this.api
      .getProjectMessages(this.selected.projectId, 80)
      .subscribe({
        next: (msgs) => {
          const arr = (msgs ?? [])
            .slice()
            .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
          this.messages = arr;
          this.loadingMessages = false;
          this.hasMore = (msgs?.length ?? 0) >= 80;
          queueMicrotask(() => this.scrollToBottom());
        },
        error: (err) => {
          console.error(err);
          this.loadingMessages = false;
          this.errorMessages = 'Erreur lors du chargement des messages.';
        },
      });

    this.startLiveRefresh();

    this.sub.add(s);
  }

  // Scroll-top -> load older via before=
  loadOlder(): void {
    if (!this.selected) return;
    if (this.loadingOlder || !this.hasMore) return;
    if (!this.messages.length) return;

    const viewport = this.messagesViewport?.nativeElement;
    if (!viewport) return;

    this.loadingOlder = true;
    this.errorMessages = null;

    const before = this.messages[0].createdAt;
    const prevScrollHeight = viewport.scrollHeight;
    const prevScrollTop = viewport.scrollTop;

    const s = this.api
      .getProjectMessages(this.selected.projectId, 50, before)
      .subscribe({
        next: (older) => {
          const normalized = (older ?? [])
            .slice()
            .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

          const existing = new Set(this.messages.map((m) => m.id));
          this.messages = [
            ...normalized.filter((m) => !existing.has(m.id)),
            ...this.messages,
          ];

          this.loadingOlder = false;
          this.hasMore = (older?.length ?? 0) >= 50;

          queueMicrotask(() => {
            const newScrollHeight = viewport.scrollHeight;
            viewport.scrollTop =
              newScrollHeight - prevScrollHeight + prevScrollTop;
          });
        },
        error: (err) => {
          console.error(err);
          this.loadingOlder = false;
          this.errorMessages = 'Impossible de charger les anciens messages.';
        },
      });

    this.sub.add(s);
  }

  onMessagesScroll(): void {
    const viewport = this.messagesViewport?.nativeElement;
    if (!viewport) return;
    if (viewport.scrollTop <= 20) this.loadOlder();
  }

  // ===== Send
  send(): void {
    if (!this.selected) return;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const content = this.contentCtrl.value.trim();
    if (!content) return;

    this.sending = true;
    this.errorMessages = null;

    const s = this.api
      .sendProjectMessage(this.selected.projectId, content)
      .subscribe({
        next: (created) => {
          this.sending = false;
          this.form.reset({ content: '' });

          this.messages = [...this.messages, created].sort((a, b) =>
            a.createdAt.localeCompare(b.createdAt)
          );

          queueMicrotask(() => this.scrollToBottom());
          this.bumpConversationPreview(created.content, created.createdAt);
        },
        error: (err) => {
          console.error(err);
          this.sending = false;
          this.errorMessages = "Erreur lors de l'envoi du message.";
        },
      });

    this.sub.add(s);
  }

  private bumpConversationPreview(content: string, createdAt: string) {
    if (!this.selected) return;

    const idx = this.conversations.findIndex(
      (x) => x.projectId === this.selected!.projectId
    );
    if (idx < 0) return;

    const preview = content.length > 60 ? content.slice(0, 60) + '…' : content;
    this.conversations[idx] = {
      ...this.conversations[idx],
      lastMessagePreview: preview,
      lastMessageAt: createdAt,
    };

    const updated = [...this.conversations];
    const [item] = updated.splice(idx, 1);
    updated.unshift(item);
    this.conversations = updated;

    this.applyConversationFilter();
  }

  private scrollToBottom(): void {
    const viewport = this.messagesViewport?.nativeElement;
    if (!viewport) return;
    viewport.scrollTop = viewport.scrollHeight;
  }

  clearError(): void {
    this.errorMessages = null;
  }
}
