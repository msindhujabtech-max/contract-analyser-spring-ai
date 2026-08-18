import React, { useState, useRef, useEffect } from 'react';

const API_BASE = window.location.port === '3000'
  ? `http://${window.location.hostname}:8000`
  : '';

function App() {
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadStatus, setUploadStatus] = useState('');
  const [streaming, setStreaming] = useState(false);
  const chatEndRef = useRef(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    setUploading(true);
    setUploadStatus('Reading document...');

    const formData = new FormData();
    formData.append('file', file);

    try {
      setUploadStatus('Uploading and processing PDF...');
      const response = await fetch(`${API_BASE}/api/upload`, {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`Upload failed: ${response.statusText}`);
      }

      const result = await response.json();
      setUploadStatus(`✓ "${result.filename}" processed — ${result.chunks} chunks indexed`);
    } catch (error) {
      setUploadStatus(`✗ Error: ${error.message}`);
    } finally {
      setUploading(false);
    }
  };

  const handleSend = async () => {
    if (!question.trim() || streaming) return;

    const userMessage = { role: 'user', content: question };
    setMessages((prev) => [...prev, userMessage]);
    setQuestion('');
    setStreaming(true);

    const assistantMessage = { role: 'assistant', content: '' };
    setMessages((prev) => [...prev, assistantMessage]);

    try {
      const response = await fetch(`${API_BASE}/api/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contract_id: 1,
          user_id: 101,
          question: userMessage.content,
        }),
      });

      if (!response.ok) {
        throw new Error(`Chat failed: ${response.statusText}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        
        // Parse SSE data lines
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5);
            if (data.trim() === '[DONE]') continue;
            setMessages((prev) => {
              const updated = [...prev];
              const last = updated[updated.length - 1];
              updated[updated.length - 1] = { ...last, content: last.content + data };
              return updated;
            });
          }
        }
      }

      // Process any remaining buffer
      if (buffer.startsWith('data:')) {
        const data = buffer.slice(5);
        if (data.trim() !== '[DONE]') {
          setMessages((prev) => {
            const updated = [...prev];
            const last = updated[updated.length - 1];
            updated[updated.length - 1] = { ...last, content: last.content + data };
            return updated;
          });
        }
      }
    } catch (error) {
      setMessages((prev) => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          role: 'assistant',
          content: `Error: ${error.message}`,
        };
        return updated;
      });
    } finally {
      setStreaming(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <h1 style={styles.title}>📄 AI Contract Analyzer</h1>
        <p style={styles.subtitle}>Upload a contract PDF and ask questions using RAG-powered AI</p>
      </header>

      <div style={styles.mainGrid}>
        {/* Document Loader Segment */}
        <section style={styles.uploadSection}>
          <h2 style={styles.sectionTitle}>Document Loader</h2>
          <div style={styles.uploadArea}>
            <label style={styles.uploadLabel} htmlFor="file-upload">
              {uploading ? '⏳ Processing...' : '📁 Choose PDF File'}
            </label>
            <input
              id="file-upload"
              type="file"
              accept=".pdf"
              onChange={handleUpload}
              disabled={uploading}
              style={styles.fileInput}
              aria-label="Upload PDF contract document"
            />
            {uploadStatus && (
              <p style={styles.uploadStatus} role="status" aria-live="polite">
                {uploadStatus}
              </p>
            )}
          </div>
        </section>

        {/* RAG Query Chatbox Segment */}
        <section style={styles.chatSection}>
          <h2 style={styles.sectionTitle}>Contract Q&A</h2>
          <div style={styles.chatContainer}>
            <div style={styles.messageList} role="log" aria-label="Chat messages">
              {messages.length === 0 && (
                <div style={styles.emptyState}>
                  <p>Upload a contract and start asking questions.</p>
                  <p style={styles.emptyHint}>Try: "What are the payment terms?" or "When does the contract expire?"</p>
                </div>
              )}
              {messages.map((msg, idx) => (
                <div
                  key={idx}
                  style={{
                    ...styles.messageBubble,
                    ...(msg.role === 'user' ? styles.userMessage : styles.assistantMessage),
                  }}
                >
                  <span style={styles.roleLabel}>
                    {msg.role === 'user' ? '🧑 You' : '🤖 AI'}
                  </span>
                  <p style={styles.messageContent}>{msg.content}</p>
                </div>
              ))}
              {streaming && (
                <div style={styles.streamingIndicator} aria-live="polite">
                  <span>●●●</span>
                </div>
              )}
              <div ref={chatEndRef} />
            </div>

            <div style={styles.inputArea}>
              <input
                type="text"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Ask a question about the contract..."
                disabled={streaming}
                style={styles.textInput}
                aria-label="Type your question"
              />
              <button
                onClick={handleSend}
                disabled={streaming || !question.trim()}
                style={{
                  ...styles.sendButton,
                  opacity: streaming || !question.trim() ? 0.5 : 1,
                }}
                aria-label="Send question"
              >
                {streaming ? '...' : 'Send'}
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}

const styles = {
  container: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    maxWidth: '1200px',
    margin: '0 auto',
    padding: '24px',
    minHeight: '100vh',
    backgroundColor: '#0f1419',
    color: '#e7e9ea',
  },
  header: {
    textAlign: 'center',
    marginBottom: '32px',
    paddingBottom: '16px',
    borderBottom: '1px solid #2f3336',
  },
  title: {
    fontSize: '28px',
    fontWeight: '700',
    margin: '0 0 8px 0',
    color: '#ffffff',
  },
  subtitle: {
    fontSize: '14px',
    color: '#71767b',
    margin: 0,
  },
  mainGrid: {
    display: 'grid',
    gridTemplateColumns: '1fr 2fr',
    gap: '24px',
    alignItems: 'start',
  },
  uploadSection: {
    backgroundColor: '#16202a',
    borderRadius: '12px',
    padding: '24px',
    border: '1px solid #2f3336',
  },
  chatSection: {
    backgroundColor: '#16202a',
    borderRadius: '12px',
    padding: '24px',
    border: '1px solid #2f3336',
    display: 'flex',
    flexDirection: 'column',
    height: '70vh',
  },
  sectionTitle: {
    fontSize: '16px',
    fontWeight: '600',
    marginBottom: '16px',
    color: '#e7e9ea',
  },
  uploadArea: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '12px',
  },
  uploadLabel: {
    display: 'inline-block',
    padding: '12px 24px',
    backgroundColor: '#1d9bf0',
    color: '#ffffff',
    borderRadius: '8px',
    cursor: 'pointer',
    fontWeight: '600',
    fontSize: '14px',
    transition: 'background-color 0.2s',
  },
  fileInput: {
    display: 'none',
  },
  uploadStatus: {
    fontSize: '13px',
    color: '#71767b',
    textAlign: 'center',
    margin: 0,
    wordBreak: 'break-word',
  },
  chatContainer: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  messageList: {
    flex: 1,
    overflowY: 'auto',
    paddingRight: '8px',
    marginBottom: '16px',
  },
  emptyState: {
    textAlign: 'center',
    color: '#71767b',
    padding: '48px 16px',
  },
  emptyHint: {
    fontSize: '13px',
    fontStyle: 'italic',
    marginTop: '8px',
  },
  messageBubble: {
    padding: '12px 16px',
    borderRadius: '12px',
    marginBottom: '12px',
    maxWidth: '85%',
  },
  userMessage: {
    backgroundColor: '#1d9bf0',
    marginLeft: 'auto',
    color: '#ffffff',
  },
  assistantMessage: {
    backgroundColor: '#273340',
    marginRight: 'auto',
    color: '#e7e9ea',
  },
  roleLabel: {
    fontSize: '11px',
    fontWeight: '700',
    display: 'block',
    marginBottom: '4px',
    opacity: 0.8,
  },
  messageContent: {
    margin: 0,
    fontSize: '14px',
    lineHeight: '1.5',
    whiteSpace: 'pre-wrap',
  },
  streamingIndicator: {
    color: '#71767b',
    fontSize: '18px',
    padding: '8px 16px',
    letterSpacing: '4px',
  },
  inputArea: {
    display: 'flex',
    gap: '8px',
    borderTop: '1px solid #2f3336',
    paddingTop: '16px',
  },
  textInput: {
    flex: 1,
    padding: '12px 16px',
    borderRadius: '8px',
    border: '1px solid #2f3336',
    backgroundColor: '#273340',
    color: '#e7e9ea',
    fontSize: '14px',
    outline: 'none',
  },
  sendButton: {
    padding: '12px 24px',
    borderRadius: '8px',
    border: 'none',
    backgroundColor: '#1d9bf0',
    color: '#ffffff',
    fontWeight: '600',
    fontSize: '14px',
    cursor: 'pointer',
  },
};

export default App;
