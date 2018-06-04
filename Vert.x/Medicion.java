package DAD;

public class Medicion {

	private Float medicion;
	private Integer iddisp;
	
	public Medicion() {
		super();
	}

	public Float getMedicion() {
		return medicion;
	}

	public void setMedicion(Float medicion) {
		this.medicion = medicion;
	}

	public Integer getIddisp() {
		return iddisp;
	}

	public void setIddisp(Integer iddisp) {
		this.iddisp = iddisp;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((iddisp == null) ? 0 : iddisp.hashCode());
		result = prime * result + ((medicion == null) ? 0 : medicion.hashCode());
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
		Medicion other = (Medicion) obj;
		if (iddisp == null) {
			if (other.iddisp != null)
				return false;
		} else if (!iddisp.equals(other.iddisp))
			return false;
		if (medicion == null) {
			if (other.medicion != null)
				return false;
		} else if (!medicion.equals(other.medicion))
			return false;
		return true;
	}

	
}
