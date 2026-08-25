import ReactMarkdown from 'react-markdown'

/** The analysis summary is the one piece of model prose in the report. */
export function Markdown({ children }: { children: string }) {
  return (
    <div className="prose prose-sm dark:prose-invert max-w-none prose-headings:font-heading prose-headings:tracking-tight prose-p:leading-relaxed">
      <ReactMarkdown>{children}</ReactMarkdown>
    </div>
  )
}
