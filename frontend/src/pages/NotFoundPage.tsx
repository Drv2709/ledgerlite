import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <main className="center-screen">
      <section className="card auth-card center">
        <h1>Page not found</h1>
        <p className="muted">The page you are looking for does not exist.</p>
        <Link to="/">Go to the dashboard</Link>
      </section>
    </main>
  );
}
