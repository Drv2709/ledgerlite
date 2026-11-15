import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { FullPageSpinner } from '../components/FullPageSpinner';
import { redirectTarget } from './redirect';
import { useAuth } from './useAuth';

/** Login and register pages: logged-in users are sent on to where they were headed (or the dashboard). */
export function PublicOnlyRoute() {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <FullPageSpinner />;
  }
  if (user) {
    return <Navigate to={redirectTarget(location.state)} replace />;
  }
  return <Outlet />;
}
