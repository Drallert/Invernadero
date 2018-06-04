package DAD;

public class Comando {

	String comando;
	

	

	public Comando() {

		super();
	}

	public String getComando() {
		return comando;
	}

	public void setComando(String comando) {
		this.comando = comando;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((comando == null) ? 0 : comando.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Comando other = (Comando) obj;
		if (comando == null) {
			if (other.comando != null)
				return false;
		} else if (!comando.equals(other.comando))
			return false;
		return true;
	}
	

}
