package info.tongrenlu.service;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.mapper.ArtistMapper;
import org.springframework.stereotype.Service;

@Service
public class ArtistService extends ServiceImpl<ArtistMapper, ArtistBean> {
}
