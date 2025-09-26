import LoginForm from '../components/login/LoginForm';
import styles from './Login.module.css';

const Login = () => {
  return (
    <div className={styles.loginContainer}>
      <LoginForm />
    </div>
  );
};

export default Login;
