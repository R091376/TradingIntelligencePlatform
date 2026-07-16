import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import ChartContainer from './components/ChartContainer'
import RequireAuth from './components/RequireAuth'
import AdminUsersPage from './pages/AdminUsersPage'
import LoginPage from './pages/LoginPage'
import './App.css'
import './pages/authPages.css'

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                {/* key forces a clean chart mount when returning from admin */}
                <ChartContainer key="chart-home" />
              </RequireAuth>
            }
          />
          <Route
            path="/admin/users"
            element={
              <RequireAuth adminOnly>
                <AdminUsersPage key="admin-users" />
              </RequireAuth>
            }
          />
          <Route path="/admin" element={<Navigate to="/admin/users" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

export default App
