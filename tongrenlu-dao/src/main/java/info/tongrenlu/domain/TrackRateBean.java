package info.tongrenlu.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

@JsonInclude(Include.NON_DEFAULT)
@Data
public class TrackRateBean extends DtoBean {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private TrackBean trackBean;

    private Integer rate;

}
