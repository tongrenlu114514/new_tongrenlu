package info.tongrenlu.mapper;

import info.tongrenlu.domain.AccessBean;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccessMapper {

    void insert(AccessBean accessBean);

}
