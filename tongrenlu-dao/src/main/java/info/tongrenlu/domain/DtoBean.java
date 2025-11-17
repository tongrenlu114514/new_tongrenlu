package info.tongrenlu.domain;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

@JsonInclude(Include.NON_DEFAULT)
@Data
public class DtoBean implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private Integer id;

    @JsonIgnore
    private Date updDate;

    @JsonIgnore
    private String delFlg;

}
